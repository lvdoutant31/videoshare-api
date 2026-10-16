package com.yingliu.worker.video;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** 定时发布任务配置：批量大小为条数，扫描间隔与处理超时分别控制轮询和恢复。 */
@ConfigurationProperties("yingliu.worker.scheduled-publish")
public class ScheduledPublishProperties {
    private int batchSize = 50;
    private Duration processingTimeout = Duration.ofMinutes(5);

    public int batchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public Duration processingTimeout() { return processingTimeout; }
    public void setProcessingTimeout(Duration processingTimeout) { this.processingTimeout = processingTimeout; }
}
