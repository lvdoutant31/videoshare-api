package com.yingliu.service.app;

import com.yingliu.domain.comment.VideoComment;
import com.yingliu.media.storage.MediaStorage;
import com.yingliu.service.app.mapper.AppUserMapper;
import com.yingliu.service.app.mapper.CommentReplyNotificationMapper;
import com.yingliu.service.app.mapper.ContentReportMapper;
import com.yingliu.service.app.mapper.SysSensitiveWordMapper;
import com.yingliu.service.app.mapper.UserCommentLikeMapper;
import com.yingliu.service.app.mapper.VideoCommentMapper;
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

@ExtendWith(MockitoExtension.class)
class DefaultAppCommentServiceTest {
    private static final long USER_ID = 11L;
    private static final long COMMENT_ID = 22L;

    @Mock private VideoCommentMapper commentMapper;
    @Mock private UserCommentLikeMapper commentLikeMapper;
    @Mock private AppUserMapper userMapper;
    @Mock private MediaResourceMapper mediaMapper;
    @Mock private MediaStorage mediaStorage;
    @Mock private VideoMapper videoMapper;
    @Mock private VideoStatsMapper statsMapper;
    @Mock private ContentReportMapper reportMapper;
    @Mock private SysSensitiveWordMapper sensitiveWordMapper;
    @Mock private CommentReplyNotificationMapper notificationMapper;

    private DefaultAppCommentService service;

    @BeforeEach
    void setUp() {
        service = new DefaultAppCommentService(commentMapper, commentLikeMapper, userMapper, mediaMapper,
                mediaStorage, videoMapper, statsMapper, reportMapper, sensitiveWordMapper, notificationMapper);
    }

    @Test
    void firstLikeUpdatesCountAndHotScoreOnce() {
        arrangeVisibleComment();
        when(commentLikeMapper.insertIfAbsent(USER_ID, COMMENT_ID)).thenReturn(1);
        when(commentMapper.selectLikeCountForUpdate(COMMENT_ID)).thenReturn(6L);

        AppCommentService.TargetState result = service.setCommentLike(USER_ID, COMMENT_ID, true);

        assertEquals(new AppCommentService.TargetState(true, 6), result);
        verify(commentMapper).adjustLikeCountAndHotScore(COMMENT_ID, 1, 100);
    }

    @Test
    void duplicateLikeDoesNotUpdateCountOrHotScore() {
        arrangeVisibleComment();
        when(commentLikeMapper.insertIfAbsent(USER_ID, COMMENT_ID)).thenReturn(0);
        when(commentMapper.selectLikeCountForUpdate(COMMENT_ID)).thenReturn(5L);

        AppCommentService.TargetState result = service.setCommentLike(USER_ID, COMMENT_ID, true);

        assertEquals(new AppCommentService.TargetState(true, 5), result);
        verify(commentMapper, never()).adjustLikeCountAndHotScore(COMMENT_ID, 1, 100);
    }

    @Test
    void firstUnlikeReturnsPostMutationCount() {
        arrangeVisibleComment();
        when(commentLikeMapper.deleteByUserAndComment(USER_ID, COMMENT_ID)).thenReturn(1);
        when(commentMapper.selectLikeCountForUpdate(COMMENT_ID)).thenReturn(4L);

        AppCommentService.TargetState result = service.setCommentLike(USER_ID, COMMENT_ID, false);

        assertEquals(new AppCommentService.TargetState(false, 4), result);
        verify(commentMapper).adjustLikeCountAndHotScore(COMMENT_ID, -1, -100);
    }

    @Test
    void duplicateUnlikeDoesNotUpdateCountOrHotScore() {
        arrangeVisibleComment();
        when(commentLikeMapper.deleteByUserAndComment(USER_ID, COMMENT_ID)).thenReturn(0);
        when(commentMapper.selectLikeCountForUpdate(COMMENT_ID)).thenReturn(5L);

        AppCommentService.TargetState result = service.setCommentLike(USER_ID, COMMENT_ID, false);

        assertEquals(new AppCommentService.TargetState(false, 5), result);
        verify(commentMapper, never()).adjustLikeCountAndHotScore(COMMENT_ID, -1, -100);
    }

    private void arrangeVisibleComment() {
        VideoComment comment = new VideoComment();
        comment.setId(COMMENT_ID);
        comment.setStatus("NORMAL");
        comment.setLikeCount(5L);
        when(commentMapper.selectById(COMMENT_ID)).thenReturn(comment);
    }
}
