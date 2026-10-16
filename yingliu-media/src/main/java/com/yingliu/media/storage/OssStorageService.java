package com.yingliu.media.storage;

import com.yingliu.media.config.OssProperties;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.Callback;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import com.aliyun.oss.model.PutObjectResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** OSS 存储实现，协调本地备份、云端上传回调与失败清理。 */
@Service
@ConditionalOnProperty(name = "media.storage-type", havingValue = "oss")
public class OssStorageService implements MediaStorage {

    private static final Logger log = LoggerFactory.getLogger(OssStorageService.class);

    private final OSS ossClient;
    private final String bucketName;
    private final String urlPrefix;
    private final String endpoint;
    private final String callbackUrl;
    private final Path localRoot;
    private final ObjectMapper objectMapper;

    public OssStorageService(OssProperties properties, ObjectMapper objectMapper,
                             @Value("${media.storage-root:document/media}") String localRoot) {
        this(properties, objectMapper, Path.of(localRoot), new OSSClientBuilder().build(
                properties.endpoint(), properties.accessKeyId(), properties.accessKeySecret()));
    }

    OssStorageService(OssProperties properties, ObjectMapper objectMapper, Path localRoot, OSS ossClient) {
        this.bucketName = properties.bucketName();
        this.urlPrefix = properties.urlPrefix();
        this.endpoint = properties.endpoint();
        this.callbackUrl = properties.callbackUrl();
        this.localRoot = localRoot.toAbsolutePath().normalize();
        this.objectMapper = objectMapper;
        this.ossClient = ossClient;
        log.info("OSS 媒体存储已就绪 bucket={}", bucketName);
    }

    @Override
    public String store(String key, InputStream data, long size, String contentType) {
        Callback callback = callback();
        boolean existedBefore = ossClient.doesObjectExist(bucketName, key);
        Path localFile = null;
        try {
            localFile = saveLocal(key, data);
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType(contentType);
            if (size > 0) {
                metadata.setContentLength(size);
            }
            PutObjectRequest request = new PutObjectRequest(bucketName, key, localFile.toFile(), metadata);
            request.setCallback(callback);
            PutObjectResult result = ossClient.putObject(request);
            log.debug("媒体文件已上传至 OSS bucket={}，key={}", bucketName, key);
            return callbackUrl(result, key);
        } catch (RuntimeException ex) {
            if (!existedBefore) {
                cleanupFailedUpload(key, localFile);
            }
            throw ex;
        }
    }

    @Override
    public String getUrl(String key) {
        if (urlPrefix != null && !urlPrefix.isBlank()) {
            return urlPrefix.replaceFirst("/+$", "") + "/" + key.replaceFirst("^/+", "");
        }
        return "https://" + bucketName + "." + extractEndpoint() + "/" + key;
    }

    @Override
    public boolean exists(String key) {
        return ossClient.doesObjectExist(bucketName, key);
    }

    @Override
    public void delete(String key) {
        try {
            ossClient.deleteObject(bucketName, key);
        } finally {
            try {
                Files.deleteIfExists(resolveLocal(key));
            } catch (IOException ex) {
                log.warn("Failed to delete local media copy: key={}", key, ex);
            }
        }
        log.debug("Deleted from OSS and local disk: bucket={}, key={}", bucketName, key);
    }

    private Path saveLocal(String key, InputStream data) {
        Path target = resolveLocal(key);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(data, target, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (IOException ex) {
            throw new IllegalStateException("视频本地保存失败: " + key, ex);
        }
    }

    private Path resolveLocal(String key) {
        Path target = localRoot.resolve(key).normalize();
        if (!target.startsWith(localRoot)) {
            throw new IllegalArgumentException("Invalid storage key: " + key);
        }
        return target;
    }

    private Callback callback() {
        if (callbackUrl == null || callbackUrl.isBlank()) {
            throw new IllegalStateException("OSS_CALLBACK_URL 未配置");
        }
        Callback callback = new Callback();
        callback.setCallbackUrl(callbackUrl);
        callback.setCallbackBody("object=${object}");
        callback.setCalbackBodyType(Callback.CalbackBodyType.URL);
        return callback;
    }

    private String callbackUrl(PutObjectResult result, String key) {
        try (InputStream body = result.getCallbackResponseBody()) {
            if (body == null) throw new IllegalStateException("OSS 回调未返回文件地址");
            String url = objectMapper.readTree(body).path("url").asText();
            if (url.isBlank()) throw new IllegalStateException("OSS 回调未返回文件地址");
            if (!url.equals(getUrl(key))) {
                throw new IllegalStateException("OSS 回调文件地址与对象不匹配");
            }
            return url;
        } catch (IOException ex) {
            throw new IllegalStateException("OSS 回调响应解析失败", ex);
        }
    }

    private void cleanupFailedUpload(String key, Path localFile) {
        try {
            ossClient.deleteObject(bucketName, key);
        } catch (RuntimeException cleanupError) {
            log.warn("Failed to clean up OSS object after upload failure: bucket={}, key={}", bucketName, key,
                    cleanupError);
        }
        try {
            if (localFile != null) Files.deleteIfExists(localFile);
        } catch (IOException cleanupError) {
            log.warn("Failed to clean up local media after upload failure: key={}", key, cleanupError);
        }
    }

    private String extractEndpoint() {
        // endpoint is like "oss-cn-shanghai.aliyuncs.com", already suitable for URL construction
        return endpoint.replaceFirst("^https?://", "");
    }

    @PreDestroy
    public void shutdown() {
        if (ossClient != null) {
            ossClient.shutdown();
        }
    }
}
