package com.yingliu.service.app;

import com.yingliu.service.content.shared.CursorPage;

import java.util.List;

/**
 * App 域评论（API §2.4）：两级评论、发布/回复、点赞、删除、举报。
 * 注意：对外 ID 全部字符串化（契约：ID 以字符串返回，避免 JS 精度丢失）。
 */
public interface AppCommentService {

    /** 一级评论列表（HOT/LATEST，游标分页）；viewerId 可空（游客） */
    CursorPage<CommentView> listRootComments(long videoId, String sort, String cursor, int limit, Long viewerId);

    /** 一级评论下的回复（时间正序） */
    List<CommentView> listReplies(long rootCommentId, Long viewerId);

    /** 发布一级评论或回复；返回审核状态 */
    PublishResult publish(long userId, long videoId, String content, Long replyToCommentId);

    /** 点赞评论（目标状态幂等） */
    TargetState setCommentLike(long userId, long commentId, boolean active);

    /** 删除本人评论；一级评论删除后其回复前台一并隐藏 */
    void deleteOwned(long userId, long commentId);

    /** 举报评论 */
    void report(long userId, long commentId, String reasonCode, String description);

    /** 举报视频 */
    void reportVideo(long userId, long videoId, String reasonCode, String description);

    /** 举报用户 */
    void reportUser(long userId, long targetUserId, String reasonCode, String description);

    record CommentView(String id, String videoId, CommentUser user, String rootCommentId, String replyToCommentId,
                       String content, String status, long likeCount, long replyCount,
                       boolean liked, boolean mine, String createdAt, List<CommentView> replies) { }

    record CommentUser(String id, String nickname, String avatarUrl) { }

    record PublishResult(String id, String status, String message) { }

    record TargetState(boolean active, long count) { }
}
