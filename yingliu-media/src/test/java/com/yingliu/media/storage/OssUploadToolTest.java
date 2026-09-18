package com.yingliu.media.storage;

import com.yingliu.media.config.OssProperties;
import org.springframework.boot.context.properties.bind.Binder;
import com.yingliu.media.config.OssUploadProperties;
import com.aliyun.sts20150401.models.AssumeRoleResponseBody;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OssUploadToolTest {

    @Test
    void createsFrontendCompatibleV4PostForm() throws Exception {
        OssUploadTool tool = new OssUploadTool(
                Binder.get(environment()).bind("media.oss", OssProperties.class).get(),
                Binder.get(environment()).bind("media.oss", OssUploadProperties.class).get());
        AssumeRoleResponseBody.AssumeRoleResponseBodyCredentials credentials =
                new AssumeRoleResponseBody.AssumeRoleResponseBodyCredentials();
        credentials.accessKeyId = "temporary-id";
        credentials.accessKeySecret = "temporary-secret";
        credentials.securityToken = "temporary-token";

        Map<String, String> form = tool.createPostSignature(
                "dir/clip.mp4", credentials, Instant.parse("2026-09-04T01:02:03Z"));

        assertThat(form)
                .containsEntry("key", "dir/clip.mp4")
                .containsEntry("signature_version", "OSS4-HMAC-SHA256")
                .containsEntry("credential", "temporary-id/20260904/cn-wuhan-lr/oss/aliyun_v4_request")
                .containsEntry("date", "20260904T010203Z")
                .containsEntry("security_token", "temporary-token")
                .containsEntry("success_action_status", "200")
                .containsEntry("host", "https://bucket.oss-cn-wuhan-lr.aliyuncs.com");
        assertThat(form.get("signature")).matches("[0-9a-f]{64}");

        String policyJson = new String(
                Base64.getDecoder().decode(form.get("policy")), StandardCharsets.UTF_8);
        JsonNode policy = new ObjectMapper().readTree(policyJson);
        assertThat(policy.path("expiration").asText()).isEqualTo("2026-09-04T02:02:03.000Z");
        assertThat(policy.path("conditions").toString())
                .contains("\"bucket\":\"bucket\"")
                .contains("[\"starts-with\",\"$key\",\"dir/\"]")
                .contains("[\"content-length-range\",1,50240000]")
                .contains("\"x-oss-security-token\":\"temporary-token\"");
    }

    private static MockEnvironment environment() {
        return new MockEnvironment()
                .withProperty("media.oss.access-key-id", "config-id")
                .withProperty("media.oss.access-key-secret", "config-secret")
                .withProperty("media.oss.role-arn", "role-arn")
                .withProperty("media.oss.bucket-name", "bucket")
                .withProperty("media.oss.endpoint", "oss-cn-wuhan-lr.aliyuncs.com")
                .withProperty("media.oss.region", "cn-wuhan-lr")
                .withProperty("media.oss.upload-dir", "dir/");
    }
}
