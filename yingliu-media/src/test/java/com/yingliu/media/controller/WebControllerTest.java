package com.yingliu.media.controller;

import com.yingliu.media.storage.MediaStorage;
import com.yingliu.media.storage.OssUploadTool;
import com.yingliu.media.config.OssProperties;

import org.springframework.boot.context.properties.bind.Binder;
import com.yingliu.media.config.OssUploadProperties;
import com.aliyun.oss.OSS;
import com.aliyun.oss.model.PutObjectRequest;
import com.aliyun.oss.model.PutObjectResult;
import com.aliyun.sts20150401.models.AssumeRoleResponseBody;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WebControllerTest {

    @Test
    void postSignatureIsAvailableThroughFrontendProxyPath() throws Exception {
        AssumeRoleResponseBody.AssumeRoleResponseBodyCredentials credentials =
                new AssumeRoleResponseBody.AssumeRoleResponseBodyCredentials();
        credentials.accessKeyId = "config-access-key-id";
        credentials.accessKeySecret = "config-access-key-secret";
        credentials.securityToken = "security-token";
        OssUploadTool tool = new OssUploadTool(
                Binder.get(environment()).bind("media.oss", OssProperties.class).get(),
                Binder.get(environment()).bind("media.oss", OssUploadProperties.class).get()) {
            @Override
            public AssumeRoleResponseBody.AssumeRoleResponseBodyCredentials getCredential() {
                return credentials;
            }
        };
        SignController controller = new SignController(tool);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

        mvc.perform(get("/api/get_post_signature_for_oss_upload"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(content().json("{\"security_token\":\"security-token\"}", false));
    }

    @Test
    void backendUploadsMultipartFileToOssWithTemporaryCredentials() throws Exception {
        AssumeRoleResponseBody.AssumeRoleResponseBodyCredentials credentials =
                new AssumeRoleResponseBody.AssumeRoleResponseBodyCredentials();
        credentials.accessKeyId = "temporary-access-key-id";
        credentials.accessKeySecret = "temporary-access-key-secret";
        credentials.securityToken = "security-token";
        AtomicReference<PutObjectRequest> uploaded = new AtomicReference<>();
        OSS oss = (OSS) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{OSS.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("putObject")) {
                        uploaded.set((PutObjectRequest) args[0]);
                        PutObjectResult result = new PutObjectResult();
                        result.setETag("etag-value");
                        return result;
                    }
                    return null;
                });
        OssUploadTool tool = new OssUploadTool(
                Binder.get(environment()).bind("media.oss", OssProperties.class).get(),
                Binder.get(environment()).bind("media.oss", OssUploadProperties.class).get()) {
            @Override
            public AssumeRoleResponseBody.AssumeRoleResponseBodyCredentials getCredential() {
                return credentials;
            }

            @Override
            protected OSS createOssClient(AssumeRoleResponseBody.AssumeRoleResponseBodyCredentials ignored) {
                return oss;
            }
        };
        SignController controller = new SignController(tool);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();
        MockMultipartFile file = new MockMultipartFile(
                "file", "clip.mp4", "video/mp4", "video-data".getBytes());

        String json = mvc.perform(multipart("/api/v1/media/oss/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andReturn().getResponse().getContentAsString();

        Map<String, String> response = new ObjectMapper().readValue(json, new TypeReference<>() {});
        assertThat(response.get("key")).startsWith("dir/").endsWith("-clip.mp4");
        assertThat(response.get("url")).isEqualTo("https://cdn.example/" + response.get("key"));
        assertThat(response.get("etag")).isEqualTo("etag-value");
        assertThat(uploaded.get().getBucketName()).isEqualTo("bucket");
        assertThat(uploaded.get().getKey()).isEqualTo(response.get("key"));
        assertThat(uploaded.get().getMetadata().getContentType()).isEqualTo("video/mp4");
    }

    @Test
    void callbackReturnsCanonicalObjectUrl() {
        MediaStorage storage = new MediaStorage() {
            @Override public String store(String key, InputStream data, long size, String contentType) { return getUrl(key); }
            @Override public String getUrl(String key) { return "https://cdn.example/" + key; }
            @Override public boolean exists(String key) { return false; }
            @Override public void delete(String key) { }
        };

        assertThat(new OSSController(storage, (request, body) -> true).callback("video/a.mp4"))
                .containsEntry("url", "https://cdn.example/video/a.mp4");
    }

    @Test
    void mappedCallbackAcceptsFormBodyAndReturnsJson() throws Exception {
        MediaStorage storage = new MediaStorage() {
            @Override public String store(String key, InputStream data, long size, String contentType) { return getUrl(key); }
            @Override public String getUrl(String key) { return "https://cdn.example/" + key; }
            @Override public boolean exists(String key) { return false; }
            @Override public void delete(String key) { }
        };
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new OSSController(storage, (request, body) -> true)).build();

        mvc.perform(post("/api/v1/media/oss/callback")
                        .contentType("application/x-www-form-urlencoded")
                        .content("object=video%2Fa.mp4"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(header().exists("Content-Length"))
                .andExpect(content().json("{\"url\":\"https://cdn.example/video/a.mp4\"}"));
    }

    @Test
    void mappedCallbackRejectsInvalidSignature() throws Exception {
        MediaStorage storage = new MediaStorage() {
            @Override public String store(String key, InputStream data, long size, String contentType) { return getUrl(key); }
            @Override public String getUrl(String key) { return "https://cdn.example/" + key; }
            @Override public boolean exists(String key) { return false; }
            @Override public void delete(String key) { }
        };
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new OSSController(storage, (request, body) -> false)).build();

        mvc.perform(post("/api/v1/media/oss/callback")
                        .contentType("application/x-www-form-urlencoded")
                        .content("object=video%2Fa.mp4"))
                .andExpect(status().isBadRequest());
    }

    private static MockEnvironment environment() {
        return new MockEnvironment()
                .withProperty("media.oss.access-key-id", "config-access-key-id")
                .withProperty("media.oss.access-key-secret", "config-access-key-secret")
                .withProperty("media.oss.role-arn", "role-arn")
                .withProperty("media.oss.bucket-name", "bucket")
                .withProperty("media.oss.endpoint", "oss-cn-wuhan-lr.aliyuncs.com")
                .withProperty("media.oss.region", "cn-wuhan-lr")
                .withProperty("media.oss.url-prefix", "https://cdn.example")
                .withProperty("media.oss.upload-dir", "dir/");
    }
}
