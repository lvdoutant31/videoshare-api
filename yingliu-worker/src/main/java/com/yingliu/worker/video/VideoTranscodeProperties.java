package com.yingliu.worker.video;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("yingliu.worker.video-transcode")
public class VideoTranscodeProperties {
    private String ffmpegPath = "ffmpeg";
    private Duration processingTimeout = Duration.ofMinutes(15);

    public String ffmpegPath() { return ffmpegPath; }
    public void setFfmpegPath(String ffmpegPath) { this.ffmpegPath = ffmpegPath; }
    public Duration processingTimeout() { return processingTimeout; }
    public void setProcessingTimeout(Duration processingTimeout) { this.processingTimeout = processingTimeout; }
}
