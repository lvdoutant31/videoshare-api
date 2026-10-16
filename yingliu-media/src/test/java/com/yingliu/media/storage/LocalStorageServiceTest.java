package com.yingliu.media.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
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

    @Test
    void failedReplacementKeepsThePreviouslyPublishedFile() throws Exception {
        LocalStorageService storage = new LocalStorageService(directory.toString(), "/media");
        storage.store("ab/video.mp4", new ByteArrayInputStream("old".getBytes(StandardCharsets.UTF_8)), 3, "video/mp4");
        InputStream broken = new InputStream() {
            private boolean first = true;
            @Override public int read() throws IOException {
                if (first) { first = false; return 'n'; }
                throw new IOException("interrupted");
            }
        };

        assertThatThrownBy(() -> storage.store("ab/video.mp4", broken, 3, "video/mp4"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(Files.readString(directory.resolve("ab/video.mp4"))).isEqualTo("old");
        try (var files = Files.list(directory.resolve("ab"))) {
            assertThat(files.map(path -> path.getFileName().toString()))
                    .noneMatch(name -> name.startsWith(".upload-"));
        }
    }
}
