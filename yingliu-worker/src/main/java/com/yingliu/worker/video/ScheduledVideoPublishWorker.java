package com.yingliu.worker.video;

import com.yingliu.service.content.video.VideoAdminService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 定时领取到期发布计划并执行业务发布，任务状态与恢复规则由视频用例维护。 */
@Component
@ConditionalOnProperty(prefix = "yingliu.worker.scheduled-publish", name = "enabled", havingValue = "true")
public class ScheduledVideoPublishWorker {
    private final VideoAdminService videoService;
    private final ScheduledPublishProperties properties;

    public ScheduledVideoPublishWorker(VideoAdminService videoService, ScheduledPublishProperties properties) {
        this.videoService = videoService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${yingliu.worker.scheduled-publish.fixed-delay:5000}")
    public void publishDueVideos() {
        LocalDateTime expiredBefore = LocalDateTime.now().minus(properties.processingTimeout());
        videoService.claimDuePublishSchedules(expiredBefore, properties.batchSize())
                .forEach(videoService::publishClaimedSchedule);
    }
}
