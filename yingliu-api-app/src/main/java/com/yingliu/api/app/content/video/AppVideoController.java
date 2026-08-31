package com.yingliu.api.app.content.video;

import com.yingliu.common.api.ApiResponse;
import com.yingliu.common.web.TraceIdFilter;
import com.yingliu.service.content.shared.CursorPage;
import com.yingliu.service.content.video.VideoAppService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@Validated
@ConditionalOnBean(VideoAppService.class)
@RequestMapping("/api/v1/app")
public class AppVideoController {
    private final VideoAppService service;

    public AppVideoController(VideoAppService service) {
        this.service = service;
    }

    @GetMapping("/feed/recommend")
    public ApiResponse<AppVideoDtos.CursorPage> recommend(@RequestParam(required = false) String cursor,
                                                            @RequestParam(defaultValue = "5") @Min(1) @Max(10) int limit,
                                                            HttpServletRequest request) {
        CursorPage<VideoAppService.FeedItem> page = service.recommend(cursor, limit);
        return ApiResponse.ok(new AppVideoDtos.CursorPage(page.items().stream().map(this::feedItem).toList(), page.nextCursor()),
                traceId(request));
    }

    @GetMapping("/videos/{videoId}")
    public ApiResponse<AppVideoDtos.Detail> get(@PathVariable long videoId, HttpServletRequest request) {
        return ApiResponse.ok(detail(service.getPublicVideo(videoId)), traceId(request));
    }

    private AppVideoDtos.FeedItem feedItem(VideoAppService.FeedItem item) {
        return new AppVideoDtos.FeedItem(String.valueOf(item.id()), item.title(), item.description(), item.durationMs(),
                author(item.authorId(), item.authorNickname()), cover(item.cover()),
                item.tags().stream().map(tag -> new AppVideoDtos.Tag(String.valueOf(tag.id()), tag.name())).toList(),
                stats(item.stats()), time(item.publishedAt()), new AppVideoDtos.ViewerState(false, false));
    }

    private AppVideoDtos.Detail detail(VideoAppService.VideoDetail item) {
        return new AppVideoDtos.Detail(String.valueOf(item.id()), item.title(), item.description(), item.durationMs(),
                author(item.authorId(), item.authorNickname()), cover(item.cover()),
                item.tags().stream().map(tag -> new AppVideoDtos.Tag(String.valueOf(tag.id()), tag.name())).toList(),
                stats(item.stats()), time(item.publishedAt()), new AppVideoDtos.ViewerState(false, false));
    }

    private AppVideoDtos.Author author(long id, String nickname) {
        return new AppVideoDtos.Author(String.valueOf(id), nickname, null, false);
    }

    private AppVideoDtos.Cover cover(VideoAppService.Cover item) {
        return item == null ? null : new AppVideoDtos.Cover(String.valueOf(item.id()), item.contentType(), item.width(),
                item.height(), null);
    }

    private AppVideoDtos.Stats stats(VideoAppService.Stats item) {
        return new AppVideoDtos.Stats(item.playCount(), item.likeCount(), item.favoriteCount(), item.commentCount());
    }

    private String time(LocalDateTime value) { return value == null ? null : value.toString(); }
    private String traceId(HttpServletRequest request) { return (String) request.getAttribute(TraceIdFilter.ATTRIBUTE); }
}
