package com.yingliu.api.admin.log;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/** 只读取应用日志文件末尾，避免把整份日志载入内存。 */
@Component
public class RuntimeLogReader {
    private static final int MAX_SCAN_BYTES = 2 * 1024 * 1024;
    private final Path logFile;
    private final Instant processStartedAt;

    @Autowired
    public RuntimeLogReader(@Value("${logging.file.name:logs/yingliu-api.log}") String logFile) {
        this(logFile, Instant.ofEpochMilli(ManagementFactory.getRuntimeMXBean().getStartTime()));
    }

    RuntimeLogReader(String logFile, Instant processStartedAt) {
        this.logFile = Path.of(logFile).toAbsolutePath().normalize();
        this.processStartedAt = processStartedAt;
    }

    public Snapshot tail(int maxLines) throws IOException {
        if (!Files.isRegularFile(logFile)) return new Snapshot(false, 0, null, false, List.of());

        BasicFileAttributes attributes = Files.readAttributes(logFile, BasicFileAttributes.class);
        try (RandomAccessFile file = new RandomAccessFile(logFile.toFile(), "r")) {
            long length = file.length();
            int byteCount = (int) Math.min(length, MAX_SCAN_BYTES);
            long start = length - byteCount;
            byte[] bytes = new byte[byteCount];
            file.seek(start);
            file.readFully(bytes);

            String content = new String(bytes, StandardCharsets.UTF_8);
            if (start > 0) {
                int firstLineEnd = content.indexOf('\n');
                content = firstLineEnd < 0 ? "" : content.substring(firstLineEnd + 1);
            }
            if (byteCount == 0 || content.isEmpty()) {
                return new Snapshot(true, length, attributes.lastModifiedTime().toInstant().toString(), start > 0, List.of());
            }
            String[] allLines = content.split("\n", -1);
            int lineCount = allLines.length;
            if (lineCount > 0 && allLines[lineCount - 1].isEmpty() && content.endsWith("\n")) lineCount--;
            List<String> currentProcessLines = new ArrayList<>();
            boolean currentProcessEvent = false;
            for (int i = 0; i < lineCount; i++) {
                String line = allLines[i].endsWith("\r")
                        ? allLines[i].substring(0, allLines[i].length() - 1) : allLines[i];
                if (line.isBlank()) continue;
                Instant timestamp = timestampOf(line);
                if (timestamp != null) currentProcessEvent = !timestamp.isBefore(processStartedAt);
                if (currentProcessEvent) currentProcessLines.add(line);
            }
            int currentFrom = Math.max(0, currentProcessLines.size() - maxLines);
            List<String> lines = currentProcessLines.subList(currentFrom, currentProcessLines.size());
            return new Snapshot(true, length, attributes.lastModifiedTime().toInstant().toString(),
                    start > 0, lines);
        }
    }

    private Instant timestampOf(String line) {
        int separator = line.indexOf(' ');
        if (separator <= 0) return null;
        try {
            return OffsetDateTime.parse(line.substring(0, separator)).toInstant();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    public record Snapshot(boolean available, long sizeBytes, String modifiedAt, boolean truncated, List<String> lines) { }
}
