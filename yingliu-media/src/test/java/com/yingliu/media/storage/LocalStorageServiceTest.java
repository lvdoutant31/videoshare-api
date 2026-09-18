package com.yingliu.media.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalStorageServiceTest {
    @TempDir Path directory;

    @Test
    void storesBytesInConfiguredDirectoryAndReturnsNginxUrl() throws Exception {
        LocalStorageService storage = new LocalStorageService(directory.toString(), "http://localhost:8088/media/");
        byte[] video = "local-video".getBytes(StandardCharsets.UTF_8);
        String url = storage.store("ab/example.mp4", new ByteArrayInputStream(video), video.length, "video/mp4");
        assertThat(Files.readAllBytes(directory.resolve("ab/example.mp4"))).isEqualTo(video);
        assertThat(url).isEqualTo("http://localhost:8088/media/ab/example.mp4");
        assertThat(storage.exists("ab/example.mp4")).isTrue();
    }

    @Test
    void rejectsWritingOutsideConfiguredDirectory() {
        LocalStorageService storage = new LocalStorageService(directory.toString(), "http://localhost:8088/media");
        assertThatThrownBy(() -> storage.store("../escape.mp4", new ByteArrayInputStream(new byte[]{1}), 1, "video/mp4"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void retainsRelativeUrlsForExistingCallers() {
        assertThat(new LocalStorageService(directory.toString()).getUrl("ab/video.mp4"))
                .isEqualTo("/media/ab/video.mp4");
    }
}
