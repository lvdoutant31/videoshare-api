package com.yingliu.media.storage;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.utils.BinaryUtil;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import com.aliyun.oss.model.PutObjectResult;
import com.aliyun.sts20150401.models.AssumeRoleRequest;
import com.aliyun.sts20150401.models.AssumeRoleResponse;
import com.aliyun.sts20150401.models.AssumeRoleResponseBody;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teautil.models.RuntimeOptions;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import com.yingliu.media.config.OssProperties;
import com.yingliu.media.config.OssUploadProperties;
import org.springframework.web.multipart.MultipartFile;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 阿里云 OSS 上传工具，支持生成 V4 PostObject 表单签名和服务端中转上传。
 */
@Component
@ConditionalOnProperty(name = "media.storage-type", havingValue = "oss")
public class OssUploadTool {

    private static final String SIGNATURE_VERSION = "OSS4-HMAC-SHA256";
    private static final String SIGNING_TERMINATOR = "aliyun_v4_request";
    private static final String SUCCESS_STATUS = "200";
    private static final long MAX_CONTENT_LENGTH = 50_240_000L;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter OSS_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final DateTimeFormatter EXPIRATION_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    private final String accessKeyId;
    private final String accessKeySecret;
    private final String roleArn;
    private final String bucket;
    private final String region;
    private final String endpoint;
    private final String stsEndpoint;
    private final String uploadHost;
    private final String urlPrefix;
    private final String uploadDir;
    private final long expireTime;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OssUploadTool(OssProperties storage, OssUploadProperties upload) {
        this.accessKeyId = storage.accessKeyId();
        this.accessKeySecret = storage.accessKeySecret();
        this.roleArn = upload.roleArn();
        this.bucket = storage.bucketName();
        this.endpoint = storage.endpoint();
        this.region = upload.region();
        this.stsEndpoint = upload.stsEndpoint();
        this.uploadHost = "https://" + bucket + "." + stripProtocol(endpoint);
        this.urlPrefix = storage.urlPrefix() == null || storage.urlPrefix().isBlank()
                ? uploadHost : withProtocol(storage.urlPrefix());
        this.uploadDir = normalizeUploadDir(upload.uploadDir());
        this.expireTime = upload.signatureExpireSeconds();
    }

    /**
     * 生成浏览器 PostObject 直传所需的全部表单字段。
     */
    public Map<String, String> createPostSignature(String objectKey) throws Exception {
        return createPostSignature(objectKey, getCredential(), Instant.now());
    }

    Map<String, String> createPostSignature(
            String objectKey,
            AssumeRoleResponseBody.AssumeRoleResponseBodyCredentials credentials,
            Instant now) {
        ZonedDateTime utcNow = now.atZone(ZoneOffset.UTC);
        String date = utcNow.format(DATE_FORMATTER);
        String ossDate = utcNow.format(OSS_DATE_FORMATTER);
        String credential = credentials.accessKeyId + "/" + date + "/" + region + "/oss/"
                + SIGNING_TERMINATOR;

        Map<String, Object> policy = new LinkedHashMap<>();
        policy.put("expiration", now.plusSeconds(expireTime).atZone(ZoneOffset.UTC)
                .format(EXPIRATION_FORMATTER));
        List<Object> conditions = new ArrayList<>();
        conditions.add(Map.of("bucket", bucket));
        conditions.add(Arrays.asList("starts-with", "$key", uploadDir));
        conditions.add(Arrays.asList("content-length-range", 1, MAX_CONTENT_LENGTH));
        conditions.add(Arrays.asList("eq", "$success_action_status", SUCCESS_STATUS));
        conditions.add(Map.of("x-oss-security-token", credentials.securityToken));
        conditions.add(Map.of("x-oss-signature-version", SIGNATURE_VERSION));
        conditions.add(Map.of("x-oss-credential", credential));
        conditions.add(Map.of("x-oss-date", ossDate));
        policy.put("conditions", conditions);

        String policyBase64 = Base64.getEncoder().encodeToString(
                writePolicy(policy).getBytes(StandardCharsets.UTF_8));
        byte[] dateKey = hmacSha256(
                ("aliyun_v4" + credentials.accessKeySecret).getBytes(StandardCharsets.UTF_8), date);
        byte[] regionKey = hmacSha256(dateKey, region);
        byte[] serviceKey = hmacSha256(regionKey, "oss");
        byte[] signingKey = hmacSha256(serviceKey, SIGNING_TERMINATOR);
        String signature = BinaryUtil.toHex(hmacSha256(signingKey, policyBase64));

        String signedKey = objectKey == null || objectKey.isBlank()
                ? uploadDir + "${filename}"
                : objectKey;
        Map<String, String> result = new LinkedHashMap<>();
        result.put("key", signedKey);
        result.put("policy", policyBase64);
        result.put("signature", signature);
        result.put("signature_version", SIGNATURE_VERSION);
        result.put("credential", credential);
        result.put("date", ossDate);
        result.put("security_token", credentials.securityToken);
        result.put("success_action_status", SUCCESS_STATUS);
        result.put("host", uploadHost);
        return result;
    }

    /**
     * 以 STS 临时凭证上传文件，返回与 HTTP 上传接口一致的 key、url 和 etag。
     * 无论上传是否成功，都释放本次创建的 OSS 客户端。
     */
    public Map<String, String> uploadFile(MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        AssumeRoleResponseBody.AssumeRoleResponseBodyCredentials credentials = getCredential();
        String objectKey = uploadDir + UUID.randomUUID() + "-" + safeFilename(file.getOriginalFilename());
        OSS ossClient = createOssClient(credentials);
        try (InputStream input = file.getInputStream()) {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(file.getSize());
            metadata.setContentType(file.getContentType() == null
                    ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                    : file.getContentType());
            PutObjectResult result = ossClient.putObject(new PutObjectRequest(bucket, objectKey, input, metadata));

            Map<String, String> response = new LinkedHashMap<>();
            response.put("key", objectKey);
            response.put("url", urlPrefix.replaceFirst("/+$", "") + "/" + objectKey);
            if (result.getETag() != null) {
                response.put("etag", result.getETag());
            }
            return response;
        } finally {
            ossClient.shutdown();
        }
    }

    public AssumeRoleResponseBody.AssumeRoleResponseBodyCredentials getCredential() throws Exception {
        if (roleArn == null || roleArn.isBlank()) {
            throw new IllegalStateException("使用 STS 签名接口前必须配置 OSS_STS_ROLE_ARN");
        }
        AssumeRoleRequest request = new AssumeRoleRequest()
                .setRoleArn(roleArn)
                .setRoleSessionName("yingliu-media-upload");
        AssumeRoleResponse response = createStsClient().assumeRoleWithOptions(request, new RuntimeOptions());
        return response.body.credentials;
    }

    com.aliyun.sts20150401.Client createStsClient() throws Exception {
        Config config = new Config()
                .setAccessKeyId(accessKeyId)
                .setAccessKeySecret(accessKeySecret);
        config.endpoint = stsEndpoint;
        return new com.aliyun.sts20150401.Client(config);
    }

    protected OSS createOssClient(AssumeRoleResponseBody.AssumeRoleResponseBodyCredentials credentials) {
        return new OSSClientBuilder().build(endpoint, credentials.accessKeyId,
                credentials.accessKeySecret, credentials.securityToken);
    }

    private String writePolicy(Map<String, Object> policy) {
        try {
            return objectMapper.writeValueAsString(policy);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("生成 OSS 上传 Policy 失败", exception);
        }
    }

    private static String safeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "upload.bin";
        }
        String normalized = originalFilename.replace('\\', '/');
        String filename = normalized.substring(normalized.lastIndexOf('/') + 1)
                .replace("\r", "")
                .replace("\n", "");
        return filename.isBlank() ? "upload.bin" : filename;
    }

    private static String normalizeUploadDir(String value) {
        String normalized = value == null ? "" : value.replace('\\', '/').replaceAll("^/+|/+$", "");
        return normalized.isBlank() ? "" : normalized + "/";
    }

    private static String stripProtocol(String value) {
        return value.replaceFirst("^https?://", "").replaceFirst("/+$", "");
    }

    private static String withProtocol(String value) {
        String normalized = value.replaceFirst("/+$", "");
        return normalized.matches("^https?://.*") ? normalized : "https://" + normalized;
    }

    static byte[] hmacSha256(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("计算 HMAC-SHA256 失败", exception);
        }
    }
}
