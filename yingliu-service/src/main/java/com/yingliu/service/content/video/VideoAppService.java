package com.yingliu.service.content.video;

import com.yingliu.service.content.shared.CursorPage;

import java.time.LocalDateTime;
import java.util.List;

public interface VideoAppService {
    CursorPage<FeedItem> recommend(String cursor, int limit);
    VideoDetail getPublicVideo(long videoId);

    record FeedItem(long id, long authorId, String authorNickname, String title, String description,
                    Integer durationMs, Cover cover, List<Tag> tags, Stats stats, LocalDateTime publishedAt) { }
    record VideoDetail(long id, long authorId, String authorNickname, String title, String description,
                       Integer durationMs, Cover cover, List<Tag> tags, Stats stats, LocalDateTime publishedAt) { }
    record Cover(long id, String contentType, Integer width, Integer height) { }
    record Tag(long id, String name) { }
    record Stats(long playCount, long likeCount, long favoriteCount, long commentCount) { }
}
