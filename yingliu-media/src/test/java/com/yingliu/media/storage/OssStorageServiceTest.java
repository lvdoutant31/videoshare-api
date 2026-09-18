package com.yingliu.media.storage;

import com.yingliu.media.config.OssProperties;
import com.aliyun.oss.OSS;
import com.aliyun.oss.model.Callback;
import com.aliyun.oss.model.PutObjectRequest;
import com.aliyun.oss.model.PutObjectResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OssStorageServiceTest {

    @TempDir
    Path localRoot;

    @Test
    void storesLocallyBeforeUploadingAndReturnsCallbackUrl() throws Exception {
        AtomicReference<PutObjectRequest> uploaded = new AtomicReference<>();
        OSS oss = (OSS) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{OSS.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("doesObjectExist")) {
                        return false;
                    }
                    if (method.getName().equals("putObject")) {
                        assertThat(localRoot.resolve("video/a.mp4")).hasContent("video-data");
                        uploaded.set((PutObjectRequest) args[0]);
                        PutObjectResult result = new PutObjectResult();
                        result.setCallbackResponseBody(new ByteArrayInputStream(
                                "{\"url\":\"https://cdn.example/video/a.mp4\"}".getBytes(StandardCharsets.UTF_8)));
                        return result;
                    }
                    return null;
                });
        OssProperties properties = new OssProperties("oss-cn-hangzhou.aliyuncs.com", "id", "secret",
                "bucket", "https://cdn.example", "https://api.example/api/v1/media/oss/callback");
        OssStorageService storage = new OssStorageService(properties, new ObjectMapper(), localRoot, oss);

        String url = storage.store("video/a.mp4",
                new ByteArrayInputStream("video-data".getBytes(StandardCharsets.UTF_8)), 10, "video/mp4");

        assertThat(url).isEqualTo("https://cdn.example/video/a.mp4");
        assertThat(Files.readString(localRoot.resolve("video/a.mp4"))).isEqualTo("video-data");
        assertThat(uploaded.get().getCallback().getCallbackUrl())
                .isEqualTo("https://api.example/api/v1/media/oss/callback");
        assertThat(uploaded.get().getCallback().getCalbackBodyType()).isEqualTo(Callback.CalbackBodyType.URL);
        assertThat(uploaded.get().getCallback().getCallbackBody()).isEqualTo("object=${object}");
    }

    @Test
    void cleansUpLocalAndNewOssObjectWhenCallbackFails() {
        AtomicReference<Boolean> deleted = new AtomicReference<>(false);
        OSS oss = (OSS) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{OSS.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("doesObjectExist")) return false;
                    if (method.getName().equals("deleteObject")) {
                        deleted.set(true);
                        return null;
                    }
                    if (method.getName().equals("putObject")) {
                        PutObjectResult result = new PutObjectResult();
                        result.setCallbackResponseBody(new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)));
                        return result;
                    }
                    return null;
                });
        OssProperties properties = new OssProperties("oss-cn-hangzhou.aliyuncs.com", "id", "secret",
                "bucket", "https://cdn.example", "https://api.example/api/v1/media/oss/callback");
        OssStorageService storage = new OssStorageService(properties, new ObjectMapper(), localRoot, oss);

        assertThatThrownBy(() -> storage.store("video/fail.mp4",
                new ByteArrayInputStream("video-data".getBytes(StandardCharsets.UTF_8)), 10, "video/mp4"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(deleted).hasValue(true);
        assertThat(localRoot.resolve("video/fail.mp4")).doesNotExist();
    }
}
