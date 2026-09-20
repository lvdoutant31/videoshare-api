package com.yingliu.service.content.video;

import com.yingliu.domain.content.video.MediaResource;
import com.yingliu.domain.content.video.Video;
import com.yingliu.domain.content.video.VideoAuthor;
import com.yingliu.media.storage.MediaStorage;
import com.yingliu.service.app.AppInteractionService;
import com.yingliu.service.content.tag.mapper.ContentTagMapper;
import com.yingliu.service.content.video.mapper.MediaResourceMapper;
import com.yingliu.service.content.video.mapper.VideoAuthorMapper;
import com.yingliu.service.content.video.mapper.VideoMapper;
import com.yingliu.service.content.video.mapper.VideoStatsMapper;
import com.yingliu.service.content.video.mapper.VideoTagMapMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DefaultVideoAppServiceTest {
    @Mock private VideoMapper videoMapper;
    @Mock private VideoAuthorMapper authorMapper;
    @Mock private MediaResourceMapper mediaMapper;
    @Mock private VideoStatsMapper statsMapper;
    @Mock private VideoTagMapMapper tagMapMapper;
    @Mock private ContentTagMapper tagMapper;
    @Mock private MediaStorage mediaStorage;
    @Mock private AppInteractionService interactionService;
    @Mock private VideoTranscodeService transcodeService;

    private DefaultVideoAppService service;

    @BeforeEach
    void setUp() {
        service = new DefaultVideoAppService(videoMapper, authorMapper, mediaMapper, statsMapper, tagMapMapper,
                tagMapper, mediaStorage, interactionService, transcodeService);
        Video video = new Video();
        video.setId(41L);
        video.setAuthorId(21L);
        video.setOriginalMediaId(51L);
        video.setTitle("测试视频");
        VideoAuthor author = new VideoAuthor();
        author.setId(21L);
        author.setNickname("作者");
        author.setStatus("NORMAL");
        MediaResource original = media(51L, "VIDEO_ORIGINAL", "original/source.mp4");
        original.setDurationMs(1234);
        when(videoMapper.selectPublicById(41L)).thenReturn(video);
        when(authorMapper.selectById(21L)).thenReturn(author);
        when(mediaMapper.selectById(51L)).thenReturn(original);
        when(tagMapMapper.selectList(any())).thenReturn(java.util.List.of());
    }

    @Test
    void detailPrefersReady720pMedia() {
        MediaResource playback = media(61L, "VIDEO_TRANSCODE", "transcoded/720p/source.mp4");
        when(transcodeService.readyMediaByVideoIds(java.util.List.of(41L))).thenReturn(Map.of(41L, playback));
        when(mediaStorage.getUrl("transcoded/720p/source.mp4")).thenReturn("/media/transcoded/720p/source.mp4");

        assertThat(service.getPublicVideo(41L, null).videoUrl())
                .isEqualTo("/media/transcoded/720p/source.mp4");
    }

    @Test
    void detailFallsBackToOriginalMedia() {
        when(transcodeService.readyMediaByVideoIds(java.util.List.of(41L))).thenReturn(Map.of());
        when(mediaStorage.getUrl("original/source.mp4")).thenReturn("/media/original/source.mp4");

        assertThat(service.getPublicVideo(41L, null).videoUrl()).isEqualTo("/media/original/source.mp4");
    }

    private static MediaResource media(long id, String type, String key) {
        MediaResource value = new MediaResource();
        value.setId(id);
        value.setResourceType(type);
        value.setStorageState("READY");
        value.setRelativePath(key);
        return value;
    }
}
