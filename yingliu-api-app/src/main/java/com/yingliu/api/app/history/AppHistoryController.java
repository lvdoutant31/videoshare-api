package com.yingliu.api.app.history;

import com.yingliu.api.app.content.AppFeedAssembler;
import com.yingliu.api.app.content.video.AppVideoDtos;
import com.yingliu.api.app.security.AppActorProvider;
import com.yingliu.common.api.ApiResponse;
import com.yingliu.framework.web.TraceIdFilter;
import com.yingliu.service.app.AppHistoryService;
import com.yingliu.service.content.shared.CursorPage;
import com.yingliu.service.content.video.VideoAppService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

/**
 * App 域观看历史（§2.5）。
 */
@RestController
@ConditionalOnBean(AppHistoryService.class)
@RequestMapping("/api/v1/app")
public class AppHistoryController {
    private final AppHistoryService service;
    private final VideoAppService videoService;
    private final AppActorProvider actor;
    private final AppFeedAssembler assembler;

    public AppHistoryController(AppHistoryService service, VideoAppService videoService,
                                AppActorProvider actor, AppFeedAssembler assembler) {
        this.service = service;
        this.videoService = videoService;
        this.actor = actor;
        this.assembler = assembler;
    }

    /** 有效播放写入（前端在播放 ≥2 秒后调用） */
    @PostMapping("/me/history")
    public ApiResponse<Void> add(@RequestBody AddBody body, HttpServletRequest request) {
        service.add(actor.currentUserId(), Long.parseLong(body.videoId()), body.progressMs());
        return ApiResponse.ok(null, traceId(request));
    }

    @GetMapping("/me/history")
    public ApiResponse<CursorPage<HistoryItemDto>> list(@RequestParam(required = false) String cursor,
                                                        @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit,
                                                        HttpServletRequest request) {
        long me = actor.currentUserId();
        CursorPage<AppHistoryService.HistoryEntry> page = service.list(me, cursor, limit);
        List<HistoryItemDto> items = page.items().stream()
                .map(entry -> {
                    try {
                        AppVideoDtos.FeedItem video = assembler.feedItem(toFeed(videoService.getPublicVideo(entry.videoId(), me)));
                        return new HistoryItemDto(String.valueOf(entry.videoId()),
                                java.time.Instant.ofEpochMilli(entry.lastViewedAtEpochMillis()).toString(),
                                entry.progressMs(), video);
                    } catch (Exception e) {
                        return null; // 视频已下架则跳过
                    }
                })
                .filter(Objects::nonNull)
                .toList();
        return ApiResponse.ok(new CursorPage<>(items, page.nextCursor()), traceId(request));
    }

    @DeleteMapping("/me/history")
    public ApiResponse<Void> clear(HttpServletRequest request) {
        service.clear(actor.currentUserId());
        return ApiResponse.ok(null, traceId(request));
    }

    private VideoAppService.FeedItem toFeed(VideoAppService.VideoDetail detail) {
        return new VideoAppService.FeedItem(detail.id(), detail.authorId(), detail.authorNickname(), detail.title(),
                detail.description(), detail.durationMs(), detail.videoUrl(), detail.cover(), detail.categories(), detail.tags(), detail.stats(),
                detail.publishedAt(), detail.viewerState(), detail.authorFollowing());
    }

    private String traceId(HttpServletRequest request) {
        return (String) request.getAttribute(TraceIdFilter.ATTRIBUTE);
    }

    public record AddBody(@NotBlank String videoId, int progressMs) { }
    public record HistoryItemDto(String videoId, String lastViewedAt, int progressMs, AppVideoDtos.FeedItem video) { }
}
