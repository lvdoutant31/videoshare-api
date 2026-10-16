package com.yingliu.service.content.video;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.yingliu.domain.content.video.MediaResource;
import com.yingliu.domain.content.video.Video;
import com.yingliu.domain.content.video.VideoTranscode;
import com.yingliu.service.content.video.mapper.MediaResourceMapper;
import com.yingliu.service.content.video.mapper.VideoMapper;
import com.yingliu.service.content.video.mapper.VideoTranscodeMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoTranscodeServiceTest {
    @Mock private VideoTranscodeMapper transcodeMapper;
    @Mock private VideoMapper videoMapper;
    @Mock private MediaResourceMapper mediaMapper;

    private VideoTranscodeService service;

    @BeforeEach
    void setUp() {
        service = new VideoTranscodeService(transcodeMapper, videoMapper, mediaMapper);
    }

    @Test
    void enqueueCreatesPending720pJob() {
        when(transcodeMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        service.enqueue(41L);

        ArgumentCaptor<VideoTranscode> saved = ArgumentCaptor.forClass(VideoTranscode.class);
        verify(transcodeMapper).insert(saved.capture());
        assertThat(saved.getValue().getVideoId()).isEqualTo(41L);
        assertThat(saved.getValue().getDefinition()).isEqualTo("720P");
        assertThat(saved.getValue().getStatus()).isEqualTo("PENDING");
    }

    @Test
    void enqueueResetsAnExistingJobForReplacementMedia() {
        VideoTranscode existing = transcode(11L, 41L, 61L, "SUCCEEDED");
        existing.setErrorCode("OLD_ERROR");
        when(transcodeMapper.selectOne(any(Wrapper.class))).thenReturn(existing);

        service.enqueue(41L);

        assertThat(existing.getMediaId()).isNull();
        assertThat(existing.getStatus()).isEqualTo("PENDING");
        assertThat(existing.getErrorCode()).isNull();
        verify(transcodeMapper).updateById(existing);
    }

    @Test
    void requeuesExpiredProcessingLeases() {
        LocalDateTime expiredBefore = LocalDateTime.of(2026, 9, 20, 8, 0);
        when(transcodeMapper.requeueStale(expiredBefore)).thenReturn(2);

        assertThat(service.requeueStale(expiredBefore)).isEqualTo(2);
    }

    @Test
    void claimNextReturnsOriginalMediaOnlyAfterAtomicClaim() {
        VideoTranscode task = transcode(11L, 41L, null, "PENDING");
        Video video = new Video();
        video.setId(41L);
        video.setOriginalMediaId(51L);
        MediaResource original = media(51L, "VIDEO_ORIGINAL", "READY", "ab/source.mp4");
        original.setSha256("source-sha");
        when(transcodeMapper.selectOne(any(Wrapper.class))).thenReturn(task);
        when(transcodeMapper.claim(eq(11L), any(LocalDateTime.class))).thenReturn(1);
        when(videoMapper.selectById(41L)).thenReturn(video);
        when(mediaMapper.selectById(51L)).thenReturn(original);

        VideoTranscodeService.WorkItem claimed = service.claimNext().orElseThrow();

        assertThat(claimed.transcodeId()).isEqualTo(11L);
        assertThat(claimed.videoId()).isEqualTo(41L);
        assertThat(claimed.sourceKey()).isEqualTo("ab/source.mp4");
        assertThat(claimed.sourceSha256()).isEqualTo("source-sha");
        assertThat(claimed.leaseStartedAt()).isNotNull();
    }

    @Test
    void readyMediaByVideoIdsReturnsOnlySucceededReadyMedia() {
        VideoTranscode complete = transcode(11L, 41L, 61L, "SUCCEEDED");
        MediaResource media = media(61L, "VIDEO_TRANSCODE", "READY", "transcoded/720p/source-sha.mp4");
        when(transcodeMapper.selectList(any(Wrapper.class))).thenReturn(List.of(complete));
        when(mediaMapper.selectBatchIds(List.of(61L))).thenReturn(List.of(media));

        assertThat(service.readyMediaByVideoIds(List.of(41L))).containsEntry(41L, media);
    }

    @Test
    void completeReusesMediaWithTheSameContentAddressedPath() {
        MediaResource existing = media(61L, "VIDEO_TRANSCODE", "READY", "transcoded/720p/source-sha.mp4");
        MediaResource replacement = media(0L, "VIDEO_TRANSCODE", "READY", "transcoded/720p/source-sha.mp4");
        replacement.setId(null);
        LocalDateTime leaseStartedAt = LocalDateTime.of(2026, 9, 20, 9, 0);
        VideoTranscodeService.WorkItem item = new VideoTranscodeService.WorkItem(
                11L, 41L, "ab/source.mp4", "source-sha", leaseStartedAt);
        when(mediaMapper.selectOne(any(Wrapper.class))).thenReturn(existing);
        when(transcodeMapper.complete(11L, leaseStartedAt, 61L)).thenReturn(1);

        service.complete(item, replacement);

        assertThat(replacement.getId()).isEqualTo(61L);
        verify(mediaMapper).updateById(replacement);
        verify(transcodeMapper).complete(11L, leaseStartedAt, 61L);
    }

    private static VideoTranscode transcode(long id, long videoId, Long mediaId, String status) {
        VideoTranscode value = new VideoTranscode();
        value.setId(id);
        value.setVideoId(videoId);
        value.setMediaId(mediaId);
        value.setDefinition("720P");
        value.setStatus(status);
        return value;
    }

    private static MediaResource media(long id, String type, String state, String key) {
        MediaResource value = new MediaResource();
        value.setId(id);
        value.setResourceType(type);
        value.setStorageState(state);
        value.setRelativePath(key);
        return value;
    }
}
