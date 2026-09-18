package com.yingliu.api.app.content.video;

import java.util.List;

public final class AppVideoDtos {
    private AppVideoDtos() { }

    public record CursorPage(List<FeedItem> items, String nextCursor) { }
    public record FeedItem(String id, String title, String description, Integer durationMs, String videoUrl, Author author, Cover cover,
                           List<Tag> tags, Stats stats, String publishedAt, ViewerState viewerState) { }
    public record Detail(String id, String title, String description, Integer durationMs, String videoUrl, Author author, Cover cover,
                         List<Tag> tags, Stats stats, String publishedAt, ViewerState viewerState) { }
    public record Author(String id, String nickname, String avatarUrl, boolean following) { }
    public record Cover(String id, String contentType, Integer width, Integer height, String coverUrl) { }
    public record Tag(String id, String name) { }
    public record Stats(long playCount, long likeCount, long favoriteCount, long commentCount) { }
    public record ViewerState(boolean liked, boolean favorited) { }
}
