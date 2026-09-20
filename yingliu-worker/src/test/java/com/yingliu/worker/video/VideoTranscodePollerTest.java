package com.yingliu.worker.video;

import org.junit.jupiter.api.Test;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class VideoTranscodePollerTest {
    @Test
    void neverQueuesOverlappingTranscodes() {
        AtomicInteger runs = new AtomicInteger();
        AtomicInteger submissions = new AtomicInteger();
        AtomicReference<Runnable> submitted = new AtomicReference<>();
        Executor executor = command -> {
            submissions.incrementAndGet();
            submitted.set(command);
        };
        VideoTranscodePoller poller = new VideoTranscodePoller(runs::incrementAndGet, executor);

        poller.poll();
        poller.poll();
        assertThat(submissions).hasValue(1);

        submitted.get().run();
        poller.poll();
        assertThat(submissions).hasValue(2);
        assertThat(runs).hasValue(1);
    }
}
