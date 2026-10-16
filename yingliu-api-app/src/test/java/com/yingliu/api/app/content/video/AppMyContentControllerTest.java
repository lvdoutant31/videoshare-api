package com.yingliu.api.app.content.video;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yingliu.api.app.security.AppActorProvider;
import com.yingliu.domain.content.video.Video;
import com.yingliu.domain.content.video.MediaResource;
import com.yingliu.domain.content.video.VideoStats;
import com.yingliu.media.storage.MediaStorage;
import com.yingliu.framework.web.GlobalExceptionHandler;
import com.yingliu.service.app.mapper.AppUserMapper;
import com.yingliu.service.content.category.mapper.ContentCategoryMapper;
import com.yingliu.service.content.tag.mapper.ContentTagMapper;
import com.yingliu.service.content.video.VideoAdminService;
import com.yingliu.service.content.video.VideoTranscodeService;
import com.yingliu.service.content.video.mapper.VideoCategoryMapMapper;
import com.yingliu.service.content.video.mapper.VideoMapper;
import com.yingliu.service.content.video.mapper.VideoTagMapMapper;
import com.yingliu.service.content.video.mapper.VideoStatsMapper;
import com.yingliu.service.content.video.DiscoverQueryRows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AppMyContentControllerTest {
    private static final long CONTENT_ID = 2108384690509983745L;
    private static final String ROOT = "/api/v1/app/me/content";
    private final VideoMapper videos = mock(VideoMapper.class);
    private final VideoAdminService service = mock(VideoAdminService.class);
    private final ContentCategoryMapper categories = mock(ContentCategoryMapper.class);
    private final VideoTranscodeService transcodes = mock(VideoTranscodeService.class);
    private final MediaStorage storage = mock(MediaStorage.class);
    private final VideoStatsMapper stats = mock(VideoStatsMapper.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), Video.class);
        AppUserMapper users = mock(AppUserMapper.class);
        AppActorProvider actor = mock(AppActorProvider.class);
        when(actor.currentUserId()).thenReturn(7L);
        when(users.countNormal(7L)).thenReturn(1);
        Video video = new Video();
        video.setId(CONTENT_ID);
        video.setAuthorId(7L);
        when(videos.selectList(any())).thenReturn(List.of(video));
        when(videos.selectOne(any())).thenReturn(video);
        when(videos.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(categories.selectCount(any())).thenReturn(1L);
        when(transcodes.readyMediaByVideoIds(any())).thenReturn(Map.of());
        when(service.get(CONTENT_ID)).thenReturn(new VideoAdminService.VideoDetail(
                CONTENT_ID, 7L, "author", "title", "<p>body</p>", null,
                new VideoAdminService.MediaView(CONTENT_ID + 1, "VIDEO", "video/mp4", 100L, null, null, null, "/media/video.mp4"),
                null, "APPROVED", "PUBLISHED", "PUBLIC", "LOW", 1,
                List.of(new VideoAdminService.CategoryView(CONTENT_ID + 2, "category")),
                List.of(new VideoAdminService.TagView(CONTENT_ID + 3, "tag")), null, null, null, null));
        mvc = MockMvcBuilders.standaloneSetup(new AppMyContentController(actor, users, videos, service,
                categories, mock(ContentTagMapper.class), mock(VideoCategoryMapMapper.class), mock(VideoTagMapMapper.class), transcodes, storage, stats))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void listIdentifiersRoundTripIntoDetailEditAndOffShelfWithoutPrecisionLoss() throws Exception {
        String response = mvc.perform(get(ROOT)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var item = new ObjectMapper().readTree(response).path("data").get(0);
        assertTrue(item.path("id").isTextual());
        assertEquals(String.valueOf(CONTENT_ID), item.path("id").textValue());
        assertEquals(String.valueOf(CONTENT_ID + 1), item.path("originalMedia").path("id").textValue());
        assertEquals(String.valueOf(CONTENT_ID + 2), item.path("categories").get(0).path("id").textValue());
        assertEquals(String.valueOf(CONTENT_ID + 3), item.path("tags").get(0).path("id").textValue());
        String path = ROOT + "/" + item.path("id").textValue();
        mvc.perform(get(path)).andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(String.valueOf(CONTENT_ID)));
        mvc.perform(put(path).contentType("application/json").content("""
                {"title":"edited","description":"<p>edited body</p>","categoryIds":["2108384690509983747"],"tagIds":[],"currentVersion":1}
                """)).andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(String.valueOf(CONTENT_ID)));
        mvc.perform(post(path + "/off-shelf")).andExpect(status().isOk());
        verify(service, times(3)).get(CONTENT_ID);
        verify(videos, never()).insert(any(Video.class));
    }

    @Test
    void ownWorksExposeRealLikesAndPlaysUsingBatchQueriesForTheReturnedPage() throws Exception {
        VideoStats value = new VideoStats();
        value.setVideoId(CONTENT_ID); value.setPlayCount(12345L); value.setLikeCount(999L);
        DiscoverQueryRows.LikeCount actualLikes = new DiscoverQueryRows.LikeCount();
        actualLikes.setVideoId(CONTENT_ID); actualLikes.setLikeCount(27L);
        when(stats.selectBatchIds(any())).thenReturn(List.of(value));
        when(stats.selectLikeCounts(any())).thenReturn(List.of(actualLikes));
        Video first = new Video(); first.setId(CONTENT_ID);
        Video sentinel = new Video(); sentinel.setId(CONTENT_ID - 1);
        when(videos.selectList(any())).thenReturn(List.of(first, sentinel));
        mvc.perform(get(ROOT + "/page").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].playCount").value(12345))
                .andExpect(jsonPath("$.data.items[0].likeCount").value(27));
        verify(stats).selectBatchIds(List.of(CONTENT_ID));
        verify(stats).selectLikeCounts(List.of(CONTENT_ID));
        verify(service, never()).get(CONTENT_ID - 1);
    }

    @Test
    void missingStatisticsReturnZeroAndEmptyPagesSkipStatisticsQueries() throws Exception {
        mvc.perform(get(ROOT + "/" + CONTENT_ID)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.playCount").value(0))
                .andExpect(jsonPath("$.data.likeCount").value(0));
        clearInvocations(stats);
        when(videos.selectList(any())).thenReturn(List.of());
        mvc.perform(get(ROOT + "/page")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());
        verifyNoInteractions(stats);
    }

    @Test
    void pagedWorksReturnTheFullCountAndKeepOwnershipAndCursorFilters() throws Exception {
        Video row = new Video(); row.setId(CONTENT_ID); row.setAuthorId(7L);
        when(videos.selectList(any())).thenReturn(List.of(row, row));
        when(videos.selectCount(any())).thenReturn(127L);
        mvc.perform(get(ROOT + "/page").param("status", "PUBLISHED").param("cursor", String.valueOf(CONTENT_ID + 1)).param("limit", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(127))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.nextCursor").value(String.valueOf(CONTENT_ID)));
        ArgumentCaptor<com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Video>> query = ArgumentCaptor.forClass(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper.class);
        verify(videos).selectList(query.capture());
        String segment = query.getValue().getSqlSegment();
        assertTrue(segment.contains("author_id")); assertTrue(segment.contains("deleted")); assertTrue(segment.contains("publish_status"));
        assertTrue(segment.contains("id <"));
        assertTrue(query.getValue().getParamNameValuePairs().containsValue(7L));
        assertTrue(query.getValue().getParamNameValuePairs().containsValue(CONTENT_ID + 1));
    }

    @Test
    void pagedWorksRejectInvalidStatusCursorAndPageSize() throws Exception {
        mvc.perform(get(ROOT + "/page").param("status", "PRIVATE")).andExpect(status().isUnprocessableEntity());
        mvc.perform(get(ROOT + "/page").param("cursor", "not-an-id")).andExpect(status().isUnprocessableEntity());
        mvc.perform(get(ROOT + "/page").param("limit", "51")).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void editingUpdatesTheExistingRecordAndPreservesItsMedia() throws Exception {
        mvc.perform(put(ROOT + "/" + CONTENT_ID).contentType("application/json").content("""
                {"title":"edited","description":"<p>edited body</p>","categoryIds":["2108384690509983747"],"tagIds":[],"currentVersion":1}
                """)).andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(String.valueOf(CONTENT_ID)));
        ArgumentCaptor<LambdaUpdateWrapper<Video>> changes = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(videos).update(isNull(), changes.capture());
        changes.getValue().getSqlSegment();
        assertTrue(changes.getValue().getParamNameValuePairs().containsValue(CONTENT_ID));
        assertTrue(changes.getValue().getParamNameValuePairs().containsValue("edited"));
        assertTrue(!changes.getValue().getSqlSet().contains("original_media_id"));
        verify(videos, never()).insert(any(Video.class));
    }

    @Test
    void ownedPreviewUsesReadyPlaybackAndReturnsTheAuthor() throws Exception {
        MediaResource ready = new MediaResource();
        ready.setRelativePath("ready.mp4");
        ready.setUrl("http://localhost:8080/old-url.mp4");
        when(transcodes.readyMediaByVideoIds(any())).thenReturn(Map.of(CONTENT_ID, ready));
        when(storage.getUrl("ready.mp4")).thenReturn("/media/ready.mp4");
        mvc.perform(get(ROOT + "/" + CONTENT_ID)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.authorNickname").value("author"))
                .andExpect(jsonPath("$.data.videoUrl").value("/media/ready.mp4"))
                .andExpect(jsonPath("$.data.originalMedia.url").value("/media/video.mp4"));
    }

    @Test
    void existingOwnedContentWithChangedStateReturnsConflict() throws Exception {
        when(videos.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(0);
        mvc.perform(post(ROOT + "/" + CONTENT_ID + "/off-shelf"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STATE_CONFLICT"));
    }

    @Test
    void missingOrOtherAuthorsContentCannotBeTakenOffShelf() throws Exception {
        when(videos.selectOne(any())).thenReturn(null);
        mvc.perform(post(ROOT + "/" + CONTENT_ID + "/off-shelf"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CONTENT_NOT_FOUND"));
        verify(videos, never()).update(isNull(), any(LambdaUpdateWrapper.class));
    }
}
