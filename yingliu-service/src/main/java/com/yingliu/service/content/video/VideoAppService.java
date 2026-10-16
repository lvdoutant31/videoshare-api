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

    /** Only ready sources for an accessible public video; original resolution is not fabricated. */
    List<PlaybackSource> playbackSources(long videoId);

    record PlaybackSource(String id, String label, String url, Integer width, Integer height) { }

    /** 打开公开内容详情；图文每次打开计一次播放，视频仍由观看进度计数。 */
    VideoDetail openPublicContent(long videoId, Long viewerId);

    /** 发现页数据看板与分类/标签组合查询。 */
    DiscoverResult discover(String keyword, Long categoryId, Long tagId, int limit, int page, String sort, Long viewerId);

    record FeedItem(long id, long authorId, String authorNickname, String title, String description,
                    Integer durationMs, String videoUrl, Cover cover, List<Category> categories, List<Tag> tags, Stats stats,
                    LocalDateTime publishedAt, ViewerState viewerState, boolean authorFollowing) { }
    record VideoDetail(long id, long authorId, String authorNickname, String title, String description,
                       Integer durationMs, String videoUrl, Cover cover, List<Category> categories, List<Tag> tags, Stats stats,
                       LocalDateTime publishedAt, ViewerState viewerState, boolean authorFollowing) { }
    record DiscoverResult(Summary summary, List<CategoryStat> categories, List<DurationStat> durationStats,
                          List<Tag> tags, List<FeedItem> ranking, List<FeedItem> videos, int page, boolean hasMore) { }
    record Summary(long totalVideos, long totalWatchTimeMs, long effectivePlayCount,
                   long averageWatchTimeMs, long todayNewCount) { }
    record CategoryStat(long id, String name, long videoCount) { }
    record DurationStat(String code, String label, long sessionCount) { }
    record Cover(long id, String contentType, Integer width, Integer height, String url) { }
    record Category(long id, String name) { }
    record Tag(long id, String name) { }
    record Stats(long playCount, long likeCount, long favoriteCount, long commentCount, long watchTimeMs) { }
    record ViewerState(boolean liked, boolean favorited) { }
}
