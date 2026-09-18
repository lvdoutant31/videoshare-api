package com.yingliu.api.app.content;

import com.yingliu.api.app.content.video.AppVideoDtos;
import com.yingliu.service.content.video.VideoAppService;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 视频视图组装：service record → App DTO（推荐流/详情/作者作品列表共用） */
@Component
public class AppFeedAssembler {

    public AppVideoDtos.FeedItem feedItem(VideoAppService.FeedItem item) {
        return new AppVideoDtos.FeedItem(String.valueOf(item.id()), item.title(), item.description(), item.durationMs(),
                item.videoUrl(), author(item.authorId(), item.authorNickname(), item.authorFollowing()), cover(item.cover()),
                item.tags().stream().map(tag -> new AppVideoDtos.Tag(String.valueOf(tag.id()), tag.name())).toList(),
                stats(item.stats()), time(item.publishedAt()), viewerState(item.viewerState()));
    }

    public AppVideoDtos.Detail detail(VideoAppService.VideoDetail item) {
        return new AppVideoDtos.Detail(String.valueOf(item.id()), item.title(), item.description(), item.durationMs(),
                item.videoUrl(), author(item.authorId(), item.authorNickname(), item.authorFollowing()), cover(item.cover()),
                item.tags().stream().map(tag -> new AppVideoDtos.Tag(String.valueOf(tag.id()), tag.name())).toList(),
                stats(item.stats()), time(item.publishedAt()), viewerState(item.viewerState()));
    }

    public AppVideoDtos.Author author(long id, String nickname, boolean following) {
        return new AppVideoDtos.Author(String.valueOf(id), nickname, null, following);
    }

    public AppVideoDtos.Cover cover(VideoAppService.Cover item) {
        return item == null ? null : new AppVideoDtos.Cover(String.valueOf(item.id()), item.contentType(), item.width(),
                item.height(), item.url());
    }

    public AppVideoDtos.Stats stats(VideoAppService.Stats item) {
        return new AppVideoDtos.Stats(item.playCount(), item.likeCount(), item.favoriteCount(), item.commentCount());
    }

    public AppVideoDtos.ViewerState viewerState(VideoAppService.ViewerState item) {
        return new AppVideoDtos.ViewerState(item.liked(), item.favorited());
    }

    private String time(LocalDateTime value) {
        return value == null ? null : value.toString();
    }
}
