package com.yingliu.media.storage;

import org.apache.http.HttpEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OSSUploaderApacheTest {

    @TempDir
    Path tempDir;

    @Test
    void buildsFrontendCompatibleMultipartWithFileLast() throws Exception {
        Path file = tempDir.resolve("clip.mp4");
        Files.writeString(file, "video-data", StandardCharsets.UTF_8);
        Map<String, String> form = new LinkedHashMap<>();
        form.put("key", "dir/clip.mp4");
        form.put("policy", "policy-value");
        form.put("signature", "signature-value");
        form.put("signature_version", "OSS4-HMAC-SHA256");
        form.put("credential", "credential-value");
        form.put("date", "20260904T010203Z");
        form.put("security_token", "security-token");
        form.put("success_action_status", "200");
        form.put("host", "https://bucket.oss-cn-wuhan-lr.aliyuncs.com/");

        OSSData data = OSSData.from(form);
        HttpEntity entity = OSSUploaderApache.buildMultipartEntity(file.toFile(), data);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        entity.writeTo(output);
        String multipart = output.toString(StandardCharsets.ISO_8859_1);

        assertThat(entity.getContentType().getValue()).startsWith("multipart/form-data;");
        assertThat(multipart)
                .contains("name=\"key\"")
                .contains("name=\"x-oss-signature\"")
                .contains("name=\"x-oss-signature-version\"")
                .contains("name=\"x-oss-security-token\"")
                .contains("name=\"success_action_status\"")
                .contains("name=\"file\"; filename=\"clip.mp4\"");
        assertThat(multipart.indexOf("name=\"file\""))
                .isGreaterThan(multipart.indexOf("name=\"success_action_status\""));
    }

    @Test
    void reportsSuccessfulStatusCodes() {
        assertThat(new OSSUploaderApache.UploadResult(200, "").isSuccessful()).isTrue();
        assertThat(new OSSUploaderApache.UploadResult(403, "denied").isSuccessful()).isFalse();
    }
}
