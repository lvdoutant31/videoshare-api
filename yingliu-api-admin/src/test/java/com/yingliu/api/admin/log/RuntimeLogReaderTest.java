package com.yingliu.api.admin.log;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeLogReaderTest {
    @TempDir
    Path tempDir;

    @Test
    void readsOnlyRequestedTailAndPreservesChineseText() throws Exception {
        Path file = tempDir.resolve("yingliu-api.log");
        Files.writeString(file, "2026-10-10T00:00:01Z INFO 第一条日志\n"
                + "2026-10-10T00:00:02Z INFO 第二条日志\n"
                + "2026-10-10T00:00:03Z INFO 第三条日志\n");

        RuntimeLogReader.Snapshot result = new RuntimeLogReader(
                file.toString(), Instant.parse("2026-10-10T00:00:00Z")).tail(2);

        assertThat(result.available()).isTrue();
        assertThat(result.lines()).containsExactly(
                "2026-10-10T00:00:02Z INFO 第二条日志",
                "2026-10-10T00:00:03Z INFO 第三条日志");
        assertThat(result.modifiedAt()).isNotBlank();
    }

    @Test
    void hidesPreviousProcessLogsAndKeepsCurrentExceptionStackTrace() throws Exception {
        Path file = tempDir.resolve("yingliu-api.log");
        Files.writeString(file, "2026-10-09T23:59:59Z INFO 上次启动的日志\n"
                + "2026-10-10T00:00:02Z ERROR 本次启动的异常\n"
                + "    at com.example.Service.run(Service.java:10)\n");

        RuntimeLogReader.Snapshot result = new RuntimeLogReader(
                file.toString(), Instant.parse("2026-10-10T00:00:00Z")).tail(100);

        assertThat(result.lines()).containsExactly(
                "2026-10-10T00:00:02Z ERROR 本次启动的异常",
                "    at com.example.Service.run(Service.java:10)");
    }

    @Test
    void reportsMissingLogFileWithoutFailing() throws Exception {
        RuntimeLogReader.Snapshot result = new RuntimeLogReader(
                tempDir.resolve("missing.log").toString(), Instant.now()).tail(100);

        assertThat(result.available()).isFalse();
        assertThat(result.lines()).isEmpty();
    }

    @Test
    void springCanInstantiateReaderUsingConfiguredConstructor() throws Exception {
        Path file = tempDir.resolve("spring-reader.log");
        Files.writeString(file, "2026-10-10T00:00:01Z INFO 已启动\n");

        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource("test", Map.of("logging.file.name", file.toString())));
            context.registerBean(RuntimeLogReader.class);
            context.refresh();

            assertThat(context.getBean(RuntimeLogReader.class).tail(100).available()).isTrue();
        }
    }
}
