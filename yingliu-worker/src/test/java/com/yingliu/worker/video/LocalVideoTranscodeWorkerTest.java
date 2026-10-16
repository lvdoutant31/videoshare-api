package com.yingliu.worker.video;

import com.yingliu.domain.content.video.MediaResource;
import com.yingliu.media.storage.MediaStorage;
import com.yingliu.service.content.video.VideoTranscodeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class LocalVideoTranscodeWorkerTest {
    @TempDir Path mediaRoot;
    private final RecordingService service = new RecordingService();
    private final RecordingStorage storage = new RecordingStorage();

    @Test
    void uses720pFaststartCommandAndPublishesMedia() throws Exception {
        Path source = mediaRoot.resolve("ab/source.mp4");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "source");
        service.next = workItem();
        AtomicReference<List<String>> command = new AtomicReference<>();
        LocalVideoTranscodeWorker.CommandExecutor executor = (arguments, timeout) -> {
            command.set(arguments);
            Files.writeString(Path.of(arguments.get(arguments.size() - 1)), "encoded-video");
            return new LocalVideoTranscodeWorker.CommandResult(0, "ok", false);
        };
        LocalVideoTranscodeWorker worker = worker(executor);

        worker.processNext();

        assertThat(command.get()).containsSubsequence("-c:v", "libx264", "-profile:v", "main")
                .containsSubsequence("-crf", "24")
                .containsSubsequence("-c:a", "aac", "-b:a", "128k")
                .containsSubsequence("-movflags", "+faststart");
        assertThat(command.get()).anyMatch(value -> value.contains("min(1280"));
        assertThat(service.completedId).isEqualTo(11L);
        assertThat(service.completedMedia.getRelativePath()).isEqualTo("transcoded/720p/source-sha.mp4");
        assertThat(service.completedMedia.getResourceType()).isEqualTo("VIDEO_TRANSCODE");
        assertThat(service.completedMedia.getVideoCodec()).isEqualTo("h264");
        assertThat(service.completedMedia.getAudioCodec()).isEqualTo("aac");
    }

    @Test
    void recordsNonzeroExitWithoutPublishing() throws Exception {
        prepareSource();
        LocalVideoTranscodeWorker worker = worker((arguments, timeout) ->
                new LocalVideoTranscodeWorker.CommandResult(1, "invalid input", false));

        worker.processNext();

        assertThat(service.failure).isEqualTo("11:FFMPEG_FAILED:invalid input");
        assertThat(service.completedMedia).isNull();
    }

    @Test
    void recordsTimeoutWithoutPublishing() throws Exception {
        prepareSource();
        LocalVideoTranscodeWorker worker = worker((arguments, timeout) ->
                new LocalVideoTranscodeWorker.CommandResult(-1, "", true));

        worker.processNext();

        assertThat(service.failure).isEqualTo("11:FFMPEG_TIMEOUT:FFmpeg 转码超时");
        assertThat(service.completedMedia).isNull();
    }

    @Test
    void metadataFailureNeverDeletesAContentAddressedOutput() throws Exception {
        prepareSource();
        service.completeFailure = new IllegalStateException("database unavailable");
        LocalVideoTranscodeWorker worker = worker((arguments, timeout) -> {
            Files.writeString(Path.of(arguments.get(arguments.size() - 1)), "encoded-video");
            return new LocalVideoTranscodeWorker.CommandResult(0, "ok", false);
        });

        worker.processNext();

        assertThat(service.failure).contains("TRANSCODE_ERROR");
        assertThat(storage.deletedKey).isNull();
    }

    private void prepareSource() throws Exception {
        Path source = mediaRoot.resolve("ab/source.mp4");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "source");
        service.next = workItem();
    }

    private VideoTranscodeService.WorkItem workItem() {
        return new VideoTranscodeService.WorkItem(11L, 41L, "ab/source.mp4", "source-sha",
                LocalDateTime.of(2026, 9, 20, 9, 0));
    }

    private LocalVideoTranscodeWorker worker(LocalVideoTranscodeWorker.CommandExecutor executor) {
        VideoTranscodeProperties properties = new VideoTranscodeProperties();
        properties.setFfmpegPath("ffmpeg-custom");
        properties.setProcessingTimeout(Duration.ofSeconds(30));
        return new LocalVideoTranscodeWorker(service, storage, properties, mediaRoot.toString(), executor);
    }

    private static final class RecordingService extends VideoTranscodeService {
        private VideoTranscodeService.WorkItem next;
        private long completedId;
        private MediaResource completedMedia;
        private String failure;
        private RuntimeException completeFailure;

        private RecordingService() { super(null, null, null); }
        @Override public Optional<WorkItem> claimNext() { return Optional.ofNullable(next); }
        @Override public void complete(WorkItem item, MediaResource media) {
            if (completeFailure != null) throw completeFailure;
            completedId = item.transcodeId();
            completedMedia = media;
        }
        @Override public int requeueStale(java.time.LocalDateTime expiredBefore) { return 0; }
        @Override public void fail(WorkItem item, String code, String message) {
            failure = item.transcodeId() + ":" + code + ":" + message;
        }
    }

    private static final class RecordingStorage implements MediaStorage {
        private String deletedKey;
        @Override public String store(String key, InputStream data, long size, String contentType) {
            return "/media/" + key;
        }
        @Override public String getUrl(String key) { return "/media/" + key; }
        @Override public boolean exists(String key) { return false; }
        @Override public void delete(String key) { deletedKey = key; }
    }
}
