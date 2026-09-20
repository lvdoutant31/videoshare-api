package com.yingliu.service.content.video;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yingliu.domain.content.video.MediaResource;
import com.yingliu.domain.content.video.Video;
import com.yingliu.domain.content.video.VideoTranscode;
import com.yingliu.service.content.video.mapper.MediaResourceMapper;
import com.yingliu.service.content.video.mapper.VideoMapper;
import com.yingliu.service.content.video.mapper.VideoTranscodeMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Maintains the single 720P rendition lifecycle used by app playback. */
@Service
public class VideoTranscodeService {
    public static final String DEFINITION = "720P";

    private final VideoTranscodeMapper transcodeMapper;
    private final VideoMapper videoMapper;
    private final MediaResourceMapper mediaMapper;

    public VideoTranscodeService(VideoTranscodeMapper transcodeMapper, VideoMapper videoMapper,
                                 MediaResourceMapper mediaMapper) {
        this.transcodeMapper = transcodeMapper;
        this.videoMapper = videoMapper;
        this.mediaMapper = mediaMapper;
    }

    public void enqueue(long videoId) {
        VideoTranscode task = transcodeMapper.selectOne(new LambdaQueryWrapper<VideoTranscode>()
                .eq(VideoTranscode::getVideoId, videoId)
                .eq(VideoTranscode::getDefinition, DEFINITION)
                .last("LIMIT 1"));
        if (task == null) {
            task = new VideoTranscode();
            task.setVideoId(videoId);
            task.setDefinition(DEFINITION);
            task.setStatus("PENDING");
            task.setRetryCount(0);
            transcodeMapper.insert(task);
            return;
        }
        task.setMediaId(null);
        task.setStatus("PENDING");
        task.setErrorCode(null);
        task.setErrorMessage(null);
        task.setStartedAt(null);
        task.setFinishedAt(null);
        transcodeMapper.updateById(task);
    }

    @Transactional
    public Optional<WorkItem> claimNext() {
        VideoTranscode task = transcodeMapper.selectOne(new LambdaQueryWrapper<VideoTranscode>()
                .eq(VideoTranscode::getDefinition, DEFINITION)
                .eq(VideoTranscode::getStatus, "PENDING")
                .orderByAsc(VideoTranscode::getCreatedAt, VideoTranscode::getId)
                .last("LIMIT 1"));
        if (task == null) return Optional.empty();
        LocalDateTime leaseStartedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        if (transcodeMapper.claim(task.getId(), leaseStartedAt) == 0) return Optional.empty();
        Video video = videoMapper.selectById(task.getVideoId());
        MediaResource original = video == null ? null : mediaMapper.selectById(video.getOriginalMediaId());
        if (original == null || !"READY".equals(original.getStorageState()) || original.getRelativePath() == null) {
            transcodeMapper.fail(task.getId(), leaseStartedAt, "SOURCE_MISSING", "原始视频文件不可用");
            return Optional.empty();
        }
        return Optional.of(new WorkItem(task.getId(), task.getVideoId(), original.getRelativePath(),
                original.getSha256(), leaseStartedAt));
    }

    @Transactional
    public void complete(WorkItem item, MediaResource media) {
        MediaResource existing = mediaMapper.selectOne(new LambdaQueryWrapper<MediaResource>()
                .eq(MediaResource::getRelativePath, media.getRelativePath())
                .last("LIMIT 1"));
        if (existing == null) {
            mediaMapper.insert(media);
        } else {
            media.setId(existing.getId());
            mediaMapper.updateById(media);
        }
        if (transcodeMapper.complete(item.transcodeId(), item.leaseStartedAt(), media.getId()) == 0) {
            throw new IllegalStateException("转码任务租约已失效: " + item.transcodeId());
        }
    }

    public void fail(WorkItem item, String code, String message) {
        transcodeMapper.fail(item.transcodeId(), item.leaseStartedAt(), code, abbreviate(message));
    }

    public int requeueStale(LocalDateTime expiredBefore) {
        return transcodeMapper.requeueStale(expiredBefore);
    }

    public Map<Long, MediaResource> readyMediaByVideoIds(Collection<Long> videoIds) {
        if (videoIds == null || videoIds.isEmpty()) return Map.of();
        List<VideoTranscode> completed = transcodeMapper.selectList(new LambdaQueryWrapper<VideoTranscode>()
                .in(VideoTranscode::getVideoId, videoIds)
                .eq(VideoTranscode::getDefinition, DEFINITION)
                .eq(VideoTranscode::getStatus, "SUCCEEDED")
                .isNotNull(VideoTranscode::getMediaId));
        if (completed.isEmpty()) return Map.of();
        Map<Long, MediaResource> media = mediaMapper.selectBatchIds(completed.stream()
                        .map(VideoTranscode::getMediaId).distinct().toList()).stream()
                .filter(item -> "VIDEO_TRANSCODE".equals(item.getResourceType()))
                .filter(item -> "READY".equals(item.getStorageState()))
                .collect(Collectors.toMap(MediaResource::getId, Function.identity()));
        return completed.stream().filter(item -> media.containsKey(item.getMediaId()))
                .collect(Collectors.toMap(VideoTranscode::getVideoId, item -> media.get(item.getMediaId())));
    }

    private String abbreviate(String message) {
        if (message == null) return null;
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    public record WorkItem(long transcodeId, long videoId, String sourceKey, String sourceSha256,
                           LocalDateTime leaseStartedAt) { }
}
