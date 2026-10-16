package com.yingliu.service.app;

import com.yingliu.domain.content.video.Video;
import com.yingliu.media.storage.MediaStorage;
import com.yingliu.service.app.mapper.AppUserMapper;
import com.yingliu.service.app.mapper.UserFollowMapper;
import com.yingliu.service.app.mapper.UserVideoFavoriteMapper;
import com.yingliu.service.app.mapper.UserVideoLikeMapper;
import com.yingliu.service.content.video.mapper.MediaResourceMapper;
import com.yingliu.service.content.video.mapper.VideoMapper;
import com.yingliu.service.content.video.mapper.VideoStatsMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.List;
import com.yingliu.domain.user.AppUser;

@ExtendWith(MockitoExtension.class)
class DefaultAppInteractionServiceTest {
    private static final long USER_ID = 11L;
    private static final long VIDEO_ID = 22L;
    private static final long AUTHOR_ID = 33L;

    @Mock private UserVideoLikeMapper likeMapper;
    @Mock private UserVideoFavoriteMapper favoriteMapper;
    @Mock private UserFollowMapper followMapper;
    @Mock private VideoMapper videoMapper;
    @Mock private VideoStatsMapper statsMapper;
    @Mock private AppUserMapper userMapper;
    @Mock private MediaResourceMapper mediaMapper;
    @Mock private MediaStorage mediaStorage;

    private DefaultAppInteractionService service;

    @Test
    void publicProfileReportsTotalWorksRatherThanLoadedPageSize() {
        AppUser user = author(33L);
        when(userMapper.selectById(33L)).thenReturn(user);
        when(videoMapper.countPublicByAuthor(33L)).thenReturn(127L);
        assertEquals(127L, service.getAuthorProfile(33L, null).worksCount());
    }

    @Test
    void unavailableAuthorCannotExposeProfileOrLikes() {
        AppUser user = author(33L); user.setStatus("BANNED");
        when(userMapper.selectById(33L)).thenReturn(user);
        assertThrows(com.yingliu.common.exception.BusinessException.class, () -> service.getAuthorProfile(33L, null));
        verify(videoMapper, never()).countPublicByAuthor(33L);
    }

    @Test
    void authorSearchPaginatesAndReturnsStringIdsWithWorksCounts() {
        long id = 2097975613254594562L;
        when(userMapper.searchAuthors("admin", null, 2)).thenReturn(List.of(author(id), author(id - 1)));
        when(videoMapper.countPublicByAuthors(List.of(id))).thenReturn(List.of(new VideoMapper.AuthorContentCount(id, 27)));
        var page = service.searchAuthors(" admin ", null, 1, null);
        assertEquals(String.valueOf(id), page.items().get(0).id());
        assertEquals(27L, page.items().get(0).worksCount());
        assertEquals(String.valueOf(id), page.nextCursor());
        verify(videoMapper).countPublicByAuthors(List.of(id));
    }

    @Test
    void likesAndFavoritesUsePublicContentPagingWithoutChangingRecords() {
        when(likeMapper.selectPublicIds(33L, 99L, 3)).thenReturn(List.of(98L, 97L, 96L));
        when(favoriteMapper.selectPublicIds(11L, null, 3)).thenReturn(List.of(22L));
        var likes = service.listLikeVideoIds(33L, "99", 2);
        assertEquals(List.of("98", "97"), likes.items());
        assertEquals("97", likes.nextCursor());
        assertEquals(List.of("22"), service.listFavoriteVideoIds(11L, null, 2).items());
        verify(likeMapper, never()).selectList(any());
        verify(favoriteMapper, never()).selectList(any());
    }

    private AppUser author(long id) {
        AppUser user = new AppUser(); user.setId(id); user.setNickname("admin"); user.setStatus("NORMAL"); user.setDeleted(0);
        return user;
    }

    @BeforeEach
    void setUp() {
        service = new DefaultAppInteractionService(likeMapper, favoriteMapper, followMapper, videoMapper,
                statsMapper, userMapper, mediaMapper, mediaStorage);
    }

    @Test
    void duplicateLikeDoesNotIncrementCounters() {
        arrangePublicVideo();
        when(likeMapper.insertIfAbsent(USER_ID, VIDEO_ID)).thenReturn(0);
        arrangeLikeCount(7);

        AppInteractionService.TargetState result = service.setVideoLike(USER_ID, VIDEO_ID, true);

        assertEquals(new AppInteractionService.TargetState(true, 7), result);
        verify(statsMapper, never()).adjustLike(VIDEO_ID, 1);
        verify(userMapper, never()).adjustLikeReceivedCount(AUTHOR_ID, 1);
    }

    @Test
    void duplicateLikeRepairsMissingDenormalizedCountFromLikeRelations() {
        arrangePublicVideo();
        when(likeMapper.insertIfAbsent(USER_ID, VIDEO_ID)).thenReturn(0);
        arrangeLikeCount(0);
        when(likeMapper.countByVideo(VIDEO_ID)).thenReturn(1L);

        AppInteractionService.TargetState result = service.setVideoLike(USER_ID, VIDEO_ID, true);

        assertEquals(new AppInteractionService.TargetState(true, 1), result);
        verify(statsMapper).setLikeCount(VIDEO_ID, 1L);
    }

    @Test
    void firstLikeIncrementsCountersOnce() {
        arrangePublicVideo();
        when(likeMapper.insertIfAbsent(USER_ID, VIDEO_ID)).thenReturn(1);
        arrangeLikeCount(8);

        AppInteractionService.TargetState result = service.setVideoLike(USER_ID, VIDEO_ID, true);

        assertEquals(new AppInteractionService.TargetState(true, 8), result);
        verify(statsMapper).adjustLike(VIDEO_ID, 1);
        verify(userMapper).adjustLikeReceivedCount(AUTHOR_ID, 1);
    }

    @Test
    void duplicateUnlikeDoesNotDecrementCounters() {
        arrangePublicVideo();
        when(likeMapper.deleteByUserAndVideo(USER_ID, VIDEO_ID)).thenReturn(0);
        arrangeLikeCount(7);

        AppInteractionService.TargetState result = service.setVideoLike(USER_ID, VIDEO_ID, false);

        assertEquals(new AppInteractionService.TargetState(false, 7), result);
        verify(statsMapper, never()).adjustLike(VIDEO_ID, -1);
        verify(userMapper, never()).adjustLikeReceivedCount(AUTHOR_ID, -1);
    }

    @Test
    void duplicateFavoriteDoesNotChangeCounter() {
        arrangePublicVideo();
        when(favoriteMapper.insertIfAbsent(USER_ID, VIDEO_ID)).thenReturn(0);
        arrangeFavoriteCount(3);

        AppInteractionService.TargetState result = service.setVideoFavorite(USER_ID, VIDEO_ID, true);

        assertEquals(new AppInteractionService.TargetState(true, 3), result);
        verify(statsMapper, never()).adjustFavorite(VIDEO_ID, 1);
    }

    @Test
    void firstUnfavoriteDecrementsCounterOnce() {
        arrangePublicVideo();
        when(favoriteMapper.deleteByUserAndVideo(USER_ID, VIDEO_ID)).thenReturn(1);
        arrangeFavoriteCount(2);

        AppInteractionService.TargetState result = service.setVideoFavorite(USER_ID, VIDEO_ID, false);

        assertEquals(new AppInteractionService.TargetState(false, 2), result);
        verify(statsMapper).adjustFavorite(VIDEO_ID, -1);
    }

    private void arrangePublicVideo() {
        Video video = new Video();
        video.setId(VIDEO_ID);
        video.setAuthorId(AUTHOR_ID);
        when(videoMapper.selectPublicById(VIDEO_ID)).thenReturn(video);
    }

    private void arrangeLikeCount(long likeCount) {
        when(statsMapper.selectLikeCountForUpdate(VIDEO_ID)).thenReturn(likeCount);
    }

    private void arrangeFavoriteCount(long favoriteCount) {
        when(statsMapper.selectFavoriteCountForUpdate(VIDEO_ID)).thenReturn(favoriteCount);
    }
}
