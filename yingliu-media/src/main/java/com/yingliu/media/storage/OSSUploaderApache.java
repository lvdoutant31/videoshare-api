package com.yingliu.media.storage;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 使用 Apache HttpClient 执行 OSS V4 PostObject 上传。
 */
public final class OSSUploaderApache {

    private static final String DEFAULT_OSS_ENDPOINT =
            "https://videoshare.oss-cn-wuhan-lr.aliyuncs.com/";
    private static final ContentType TEXT_UTF8 =
            ContentType.create(ContentType.TEXT_PLAIN.getMimeType(), StandardCharsets.UTF_8);

    private OSSUploaderApache() {
    }

    public static UploadResult upload(File file, OSSData data) throws IOException {
        if (file == null || !file.isFile()) {
            throw new IllegalArgumentException("上传文件不存在或不是普通文件");
        }
        if (data == null) {
            throw new IllegalArgumentException("OSS 签名数据不能为空");
        }

        String endpoint = data.getHost() == null || data.getHost().isBlank()
                ? DEFAULT_OSS_ENDPOINT
                : data.getHost();
        HttpPost post = new HttpPost(endpoint);
        post.setEntity(buildMultipartEntity(file, data));

        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse response = client.execute(post)) {
            HttpEntity responseEntity = response.getEntity();
            String body = responseEntity == null
                    ? ""
                    : EntityUtils.toString(responseEntity, StandardCharsets.UTF_8);
            return new UploadResult(response.getStatusLine().getStatusCode(), body);
        }
    }

    static HttpEntity buildMultipartEntity(File file, OSSData data) {
        MultipartEntityBuilder builder = MultipartEntityBuilder.create()
                .setMode(HttpMultipartMode.BROWSER_COMPATIBLE)
                .setCharset(StandardCharsets.UTF_8);

        builder.addTextBody("key", data.getKey(), TEXT_UTF8);
        builder.addTextBody("policy", data.getPolicy(), TEXT_UTF8);
        builder.addTextBody("x-oss-signature", data.getSignature(), TEXT_UTF8);
        builder.addTextBody("x-oss-signature-version", data.getSignatureVersion(), TEXT_UTF8);
        builder.addTextBody("x-oss-credential", data.getCredential(), TEXT_UTF8);
        builder.addTextBody("x-oss-date", data.getDate(), TEXT_UTF8);
        builder.addTextBody("x-oss-security-token", data.getSecurityToken(), TEXT_UTF8);
        builder.addTextBody("success_action_status", data.getSuccessActionStatus(), TEXT_UTF8);

        // OSS 要求文件字段位于其他表单字段之后。
        builder.addBinaryBody("file", file, ContentType.DEFAULT_BINARY, file.getName());
        return builder.build();
    }

    public record UploadResult(int statusCode, String body) {
        public boolean isSuccessful() {
            return statusCode >= 200 && statusCode < 300;
        }
    }
}
