package com.yingliu.worker.video;

import com.yingliu.media.storage.MediaStorage;
import com.yingliu.service.content.video.VideoTranscodeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(VideoTranscodeProperties.class)
@ConditionalOnProperty(prefix = "yingliu.worker.video-transcode", name = "enabled", havingValue = "true")
public class VideoTranscodeWorkerConfiguration {
    @Bean
    @ConditionalOnProperty(name = "media.storage-type", havingValue = "local", matchIfMissing = true)
    LocalVideoTranscodeWorker localVideoTranscodeWorker(
            VideoTranscodeService service,
            MediaStorage storage,
            VideoTranscodeProperties properties,
            @Value("${media.storage-root:document/media}") String mediaRoot) {
        return new LocalVideoTranscodeWorker(service, storage, properties, mediaRoot,
                LocalVideoTranscodeWorker::executeCommand);
    }

    @Bean
    @ConditionalOnProperty(name = "media.storage-type", havingValue = "local", matchIfMissing = true)
    VideoTranscodePoller videoTranscodePoller(LocalVideoTranscodeWorker worker) {
        return new VideoTranscodePoller(worker);
    }
}
