package com.yingliu.worker.video;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 按开关装配定时发布任务、调度和锁配置。 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(ScheduledPublishProperties.class)
@ConditionalOnProperty(prefix = "yingliu.worker.scheduled-publish", name = "enabled", havingValue = "true")
public class ScheduledPublishWorkerConfiguration {
}
