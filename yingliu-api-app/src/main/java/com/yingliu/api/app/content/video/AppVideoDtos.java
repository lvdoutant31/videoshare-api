package com.yingliu.api.app.content.video;

import java.util.List;

public final class AppVideoDtos {
    private AppVideoDtos() { }

    public record CursorPage(List<FeedItem> items, String nextCursor) { }
    public record FeedItem(String id, String title, String description, Integer durationMs, String videoUrl, Author author, Cover cover,
                           List<Category> categories, List<Tag> tags, Stats stats, String publishedAt, ViewerState viewerState) { }
    public record Detail(String id, String title, String description, Integer durationMs, String videoUrl, Author author, Cover cover,
                         List<Category> categories, List<Tag> tags, Stats stats, String publishedAt, ViewerState viewerState) { }
    public record Discover(Summary summary, List<CategoryStat> categories, List<DurationStat> durationStats,
                           List<Tag> tags, List<FeedItem> ranking, List<FeedItem> videos, int page, boolean hasMore) { }
    public record Summary(long totalVideos, long totalWatchTimeMs, long effectivePlayCount,
                          long averageWatchTimeMs, long todayNewCount) { }
    public record CategoryStat(String id, String name, long videoCount) { }
    public record DurationStat(String code, String label, long sessionCount) { }
    public record Author(String id, String nickname, String avatarUrl, boolean following) { }
    public record Cover(String id, String contentType, Integer width, Integer height, String coverUrl) { }
    public record Category(String id, String name) { }
    public record Tag(String id, String name) { }
    public record Stats(long playCount, long likeCount, long favoriteCount, long commentCount, long watchTimeMs) { }
    public record ViewerState(boolean liked, boolean favorited) { }
}
