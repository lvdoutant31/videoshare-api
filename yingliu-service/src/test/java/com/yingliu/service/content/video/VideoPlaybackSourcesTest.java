package com.yingliu.service.content.video;

import com.yingliu.domain.content.video.MediaResource;
import com.yingliu.domain.content.video.Video;
import com.yingliu.domain.content.video.VideoAuthor;
import com.yingliu.media.storage.MediaStorage;
import com.yingliu.service.app.AppInteractionService;
import com.yingliu.service.content.category.mapper.ContentCategoryMapper;
import com.yingliu.service.content.tag.mapper.ContentTagMapper;
import com.yingliu.service.content.video.mapper.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class VideoPlaybackSourcesTest {
    private final VideoMapper videos = mock(VideoMapper.class);
    private final VideoAuthorMapper authors = mock(VideoAuthorMapper.class);
    private final MediaResourceMapper media = mock(MediaResourceMapper.class);
    private final MediaStorage storage = mock(MediaStorage.class);
    private final VideoTranscodeService transcodes = mock(VideoTranscodeService.class);
    private final DefaultVideoAppService service = new DefaultVideoAppService(videos, authors, media,
            mock(VideoStatsMapper.class), mock(VideoTagMapMapper.class), mock(ContentTagMapper.class),
            mock(VideoCategoryMapMapper.class), mock(ContentCategoryMapper.class), storage,
            mock(AppInteractionService.class), transcodes);

    private MediaResource original() {
        Video video = new Video(); video.setId(1L); video.setAuthorId(2L); video.setOriginalMediaId(3L);
        VideoAuthor author = new VideoAuthor(); author.setStatus("NORMAL");
        MediaResource original = new MediaResource(); original.setResourceType("VIDEO_ORIGINAL");
        original.setStorageState("READY"); original.setRelativePath("original.mp4");
        when(videos.selectPublicById(1L)).thenReturn(video);
        when(authors.selectById(2L)).thenReturn(author);
        when(media.selectById(3L)).thenReturn(original);
        when(storage.getUrl("original.mp4")).thenReturn("/media/original.mp4");
        return original;
    }

    @Test void returnsOnlyReadySourcesAndKeepsOriginal() {
        original();
        MediaResource optimized = new MediaResource(); optimized.setRelativePath("optimized.mp4");
        optimized.setWidth(1280); optimized.setHeight(720);
        when(transcodes.readyMediaByVideoIds(List.of(1L))).thenReturn(Map.of(1L, optimized));
        when(storage.getUrl("optimized.mp4")).thenReturn("/media/optimized.mp4");
        var sources = service.playbackSources(1L);
        assertThat(sources).extracting(VideoAppService.PlaybackSource::id).containsExactly("optimized", "original");
        assertThat(sources.get(0).height()).isEqualTo(720);
    }

    @Test void incompleteTranscodeOffersOnlyOriginal() {
        original();
        when(transcodes.readyMediaByVideoIds(List.of(1L))).thenReturn(Map.of());
        assertThat(service.playbackSources(1L)).extracting(VideoAppService.PlaybackSource::id).containsExactly("original");
    }

    @Test void privateVideoDoesNotExposeAnySource() {
        assertThatThrownBy(() -> service.playbackSources(1L)).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(storage, transcodes);
    }

    @Test void missingOriginalIsRejected() {
        original().setStorageState("FAILED");
        assertThatThrownBy(() -> service.playbackSources(1L)).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(transcodes);
    }
}
