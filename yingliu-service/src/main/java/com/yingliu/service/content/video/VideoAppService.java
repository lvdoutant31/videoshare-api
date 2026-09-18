package com.yingliu.service.content.video;

import com.yingliu.service.content.shared.CursorPage;

import java.time.LocalDateTime;
import java.util.List;

/** 公开视频查询契约，返回播放信息与可选登录用户的互动状态。 */
public interface VideoAppService {
    /** 推荐流（游客或登录用户；登录时返回 viewerState 与关注状态） */
    CursorPage<FeedItem> recommend(String cursor, int limit, Long viewerId);

    /** 关注流（仅返回当前用户已关注作者的公开视频） */
    CursorPage<FeedItem> following(String cursor, int limit, long viewerId);

    /** 视频详情（同上，可选鉴权） */
    VideoDetail getPublicVideo(long videoId, Long viewerId);

    record FeedItem(long id, long authorId, String authorNickname, String title, String description,
                    Integer durationMs, String videoUrl, Cover cover, List<Tag> tags, Stats stats,
                    LocalDateTime publishedAt, ViewerState viewerState, boolean authorFollowing) { }
    record VideoDetail(long id, long authorId, String authorNickname, String title, String description,
                       Integer durationMs, String videoUrl, Cover cover, List<Tag> tags, Stats stats,
                       LocalDateTime publishedAt, ViewerState viewerState, boolean authorFollowing) { }
    record Cover(long id, String contentType, Integer width, Integer height, String url) { }
    record Tag(long id, String name) { }
    record Stats(long playCount, long likeCount, long favoriteCount, long commentCount) { }
    record ViewerState(boolean liked, boolean favorited) { }
}
