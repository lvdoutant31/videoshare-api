package com.yingliu.api.admin.test.oss;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yingliu.api.admin.security.AdminPermissionAuthorizer;
import com.yingliu.api.admin.security.AdminPermissionCache;
import com.yingliu.api.admin.security.FakeTtlStore;
import com.yingliu.media.storage.MediaStorage;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class OssTestControllerTest {

    @Test
    void uploadsFileWithoutInvokingVideoServices() {
        AtomicReference<String> key = new AtomicReference<>();
        AtomicReference<Long> size = new AtomicReference<>();
        AtomicReference<String> contentType = new AtomicReference<>();
        MediaStorage storage = new MediaStorage() {
            @Override
            public String store(String objectKey, InputStream data, long objectSize, String type) {
                key.set(objectKey);
                size.set(objectSize);
                contentType.set(type);
                return "https://cdn.example/" + objectKey;
            }

            @Override public String getUrl(String objectKey) { return ""; }
            @Override public boolean exists(String objectKey) { return false; }
            @Override public void delete(String objectKey) { }
        };

        var response = new OssTestController(storage, allowingAuthorizer(), 1024)
                .upload(new MockMultipartFile("file", "sample.txt", "TEXT/PLAIN", "hello".getBytes()),
                        new MockHttpServletRequest());

        assertThat(response.code()).isEqualTo("OK");
        assertThat(response.data()).containsEntry("fileName", "sample.txt");
        assertThat(response.data()).containsEntry("size", 5L);
        assertThat(response.data()).containsEntry("contentType", "text/plain");
        assertThat(key.get()).startsWith("test-uploads/");
        assertThat(key.get()).endsWith(".txt");
        assertThat(size.get()).isEqualTo(5L);
        assertThat(contentType.get()).isEqualTo("text/plain");
    }

    @Test
    void rejectsFilesOverConfiguredLimitBeforeStorage() {
        MediaStorage storage = new MediaStorage() {
            @Override public String store(String key, InputStream data, long size, String contentType) {
                throw new AssertionError("storage should not be called");
            }
            @Override public String getUrl(String key) { return ""; }
            @Override public boolean exists(String key) { return false; }
            @Override public void delete(String key) { }
        };

        long limit = 1024 * 1024;
        var controller = new OssTestController(storage, allowingAuthorizer(), limit);
        var file = new MockMultipartFile("file", "large.bin", "application/octet-stream",
                new byte[(int) limit + 1]);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                controller.upload(file, new MockHttpServletRequest()))
                .hasMessage("文件不能超过 1 MB");
    }

    /**
     * 本用例只关心上传流程，因此用放行的鉴权替身。
     * 鉴权本身的行为由 {@code AdminPermissionAuthorizerTest} 覆盖。
     */
    private static AdminPermissionAuthorizer allowingAuthorizer() {
        JdbcTemplate unusedJdbcTemplate = new JdbcTemplate();
        AdminPermissionCache unusedCache = new AdminPermissionCache(
                unusedJdbcTemplate, new FakeTtlStore(), new ObjectMapper());
        return new AdminPermissionAuthorizer(() -> 1L, unusedJdbcTemplate, unusedCache) {
            @Override
            public long require(String permission) {
                return 1L;
            }
        };
    }
}
