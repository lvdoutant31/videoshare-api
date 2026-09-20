package com.yingliu.worker.video;

import jakarta.annotation.PreDestroy;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** Schedules transcodes without blocking Spring's shared scheduling thread. */
public class VideoTranscodePoller {
    private final Runnable work;
    private final Executor executor;
    private final ExecutorService ownedExecutor;
    private final AtomicBoolean running = new AtomicBoolean();

    public VideoTranscodePoller(LocalVideoTranscodeWorker worker) {
        this.work = worker::processNext;
        this.ownedExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "video-transcode-1");
            thread.setDaemon(true);
            return thread;
        });
        this.executor = ownedExecutor;
    }

    VideoTranscodePoller(Runnable work, Executor executor) {
        this.work = work;
        this.executor = executor;
        this.ownedExecutor = null;
    }

    @Scheduled(fixedDelayString = "${yingliu.worker.video-transcode.fixed-delay:5000}")
    public void poll() {
        if (!running.compareAndSet(false, true)) return;
        try {
            executor.execute(() -> {
                try {
                    work.run();
                } finally {
                    running.set(false);
                }
            });
        } catch (RuntimeException exception) {
            running.set(false);
            throw exception;
        }
    }

    @PreDestroy
    public void close() {
        if (ownedExecutor != null) ownedExecutor.shutdown();
    }
}
