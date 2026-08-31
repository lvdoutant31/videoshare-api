package com.yingliu.worker.video;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("yingliu.worker.scheduled-publish")
public class ScheduledPublishProperties {
    private int batchSize = 50;
    private Duration processingTimeout = Duration.ofMinutes(5);

    public int batchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public Duration processingTimeout() { return processingTimeout; }
    public void setProcessingTimeout(Duration processingTimeout) { this.processingTimeout = processingTimeout; }
}
