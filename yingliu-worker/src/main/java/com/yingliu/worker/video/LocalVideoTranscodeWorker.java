package com.yingliu.worker.video;

import com.yingliu.domain.content.video.MediaResource;
import com.yingliu.media.storage.MediaStorage;
import com.yingliu.service.content.video.VideoTranscodeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Converts one pending local video at a time into a browser-compatible 720P MP4. */
public class LocalVideoTranscodeWorker {
    private static final Logger log = LoggerFactory.getLogger(LocalVideoTranscodeWorker.class);
    private static final int MAX_DIAGNOSTIC_BYTES = 64 * 1024;

    private final VideoTranscodeService service;
    private final MediaStorage storage;
    private final VideoTranscodeProperties properties;
    private final Path mediaRoot;
    private final CommandExecutor executor;

    public LocalVideoTranscodeWorker(VideoTranscodeService service, MediaStorage storage,
                                     VideoTranscodeProperties properties, String mediaRoot,
                                     CommandExecutor executor) {
        this.service = service;
        this.storage = storage;
        this.properties = properties;
        this.mediaRoot = Path.of(mediaRoot).toAbsolutePath().normalize();
        this.executor = executor;
    }

    public void processNext() {
        service.requeueStale(LocalDateTime.now().minus(properties.processingTimeout()));
        service.claimNext().ifPresent(this::process);
    }

    private void process(VideoTranscodeService.WorkItem item) {
        Path temporary = null;
        String outputKey = outputKey(item);
        try {
            Path source = mediaRoot.resolve(item.sourceKey()).normalize();
            if (!source.startsWith(mediaRoot) || !Files.isRegularFile(source)) {
                service.fail(item, "SOURCE_MISSING", "原始视频文件不存在");
                return;
            }
            Path temporaryDirectory = mediaRoot.resolve(".transcode-tmp");
            Files.createDirectories(temporaryDirectory);
            temporary = Files.createTempFile(temporaryDirectory, "video-", ".mp4");
            CommandResult result = executor.execute(command(source, temporary), properties.processingTimeout());
            if (result.timedOut()) {
                service.fail(item, "FFMPEG_TIMEOUT", "FFmpeg 转码超时");
                return;
            }
            if (result.exitCode() != 0) {
                service.fail(item, "FFMPEG_FAILED", result.output());
                return;
            }
            if (!Files.isRegularFile(temporary) || Files.size(temporary) == 0) {
                service.fail(item, "OUTPUT_MISSING", "FFmpeg 未生成播放文件");
                return;
            }
            MediaResource media = media(temporary, outputKey);
            try (InputStream input = Files.newInputStream(temporary)) {
                media.setUrl(storage.store(outputKey, input, media.getFileSize(), media.getContentType()));
            }
            service.complete(item, media);
            log.info("Video transcode completed: videoId={}, key={}", item.videoId(), outputKey);
        } catch (Exception exception) {
            log.warn("Video transcode failed: videoId={}", item.videoId(), exception);
            service.fail(item, "TRANSCODE_ERROR", safeMessage(exception));
        } finally {
            if (temporary != null) {
                try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
            }
        }
    }

    List<String> command(Path source, Path output) {
        List<String> command = new ArrayList<>();
        command.add(properties.ffmpegPath());
        command.addAll(List.of("-y", "-i", source.toString(),
                "-vf", "scale='if(gt(iw,ih),min(1280,trunc(iw/2)*2),-2)':'if(gt(iw,ih),-2,min(1280,trunc(ih/2)*2))'",
                "-c:v", "libx264", "-preset", "fast", "-crf", "24", "-profile:v", "main",
                "-pix_fmt", "yuv420p", "-r", "30", "-maxrate", "2500k", "-bufsize", "5000k",
                "-c:a", "aac", "-b:a", "128k", "-movflags", "+faststart", output.toString()));
        return command;
    }

    private String outputKey(VideoTranscodeService.WorkItem item) {
        String identity = item.sourceSha256() == null || item.sourceSha256().isBlank()
                ? "video-" + item.videoId() : item.sourceSha256();
        return "transcoded/720p/" + identity + ".mp4";
    }

    private MediaResource media(Path file, String key) throws Exception {
        MediaResource media = new MediaResource();
        media.setResourceType("VIDEO_TRANSCODE");
        media.setStorageState("READY");
        media.setRelativePath(key);
        media.setContentType("video/mp4");
        media.setFileSize(Files.size(file));
        media.setSha256(sha256(file));
        media.setVideoCodec("h264");
        media.setAudioCodec("aac");
        media.setBackupStatus("READY");
        return media;
    }

    private String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private String safeMessage(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    static CommandResult executeCommand(List<String> command, Duration timeout) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        CompletableFuture<String> diagnostics = CompletableFuture.supplyAsync(() -> drain(process.getInputStream()));
        boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
            try { process.getInputStream().close(); } catch (IOException ignored) { }
            return new CommandResult(-1, diagnostics.get(5, TimeUnit.SECONDS), true);
        }
        return new CommandResult(process.exitValue(), diagnostics.get(5, TimeUnit.SECONDS), false);
    }

    private static String drain(InputStream input) {
        try (input; ByteArrayOutputStream captured = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            int remaining = MAX_DIAGNOSTIC_BYTES;
            while ((read = input.read(buffer)) != -1) {
                if (remaining > 0) {
                    int kept = Math.min(read, remaining);
                    captured.write(buffer, 0, kept);
                    remaining -= kept;
                }
            }
            return captured.toString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            return exception.getMessage() == null ? "" : exception.getMessage();
        }
    }

    @FunctionalInterface
    interface CommandExecutor {
        CommandResult execute(List<String> command, Duration timeout) throws Exception;
    }

    record CommandResult(int exitCode, String output, boolean timedOut) { }
}
