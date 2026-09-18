package com.yingliu.api.app.content.video;

import com.yingliu.api.app.content.AppFeedAssembler;
import com.yingliu.api.app.security.AppActorProvider;
import com.yingliu.common.api.ApiResponse;
import com.yingliu.framework.web.TraceIdFilter;
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

@RestController
@Validated
@ConditionalOnBean(VideoAppService.class)
@RequestMapping("/api/v1/app")
public class AppVideoController {
    private final VideoAppService service;
    private final AppActorProvider actor;
    private final AppFeedAssembler assembler;

    public AppVideoController(VideoAppService service, AppActorProvider actor, AppFeedAssembler assembler) {
        this.service = service;
        this.actor = actor;
        this.assembler = assembler;
    }

    @GetMapping("/feed/recommend")
    public ApiResponse<AppVideoDtos.CursorPage> recommend(@RequestParam(required = false) String cursor,
                                                            @RequestParam(defaultValue = "5") @Min(1) @Max(10) int limit,
                                                            HttpServletRequest request) {
        CursorPage<VideoAppService.FeedItem> page = service.recommend(cursor, limit, actor.optionalUserId());
        return ApiResponse.ok(new AppVideoDtos.CursorPage(page.items().stream().map(assembler::feedItem).toList(), page.nextCursor()),
                traceId(request));
    }

    @GetMapping("/feed/following")
    public ApiResponse<AppVideoDtos.CursorPage> following(@RequestParam(required = false) String cursor,
                                                           @RequestParam(defaultValue = "5") @Min(1) @Max(10) int limit,
                                                           HttpServletRequest request) {
        CursorPage<VideoAppService.FeedItem> page = service.following(cursor, limit, actor.currentUserId());
        return ApiResponse.ok(new AppVideoDtos.CursorPage(page.items().stream().map(assembler::feedItem).toList(), page.nextCursor()),
                traceId(request));
    }

    @GetMapping("/videos/{videoId}")
    public ApiResponse<AppVideoDtos.Detail> get(@PathVariable long videoId, HttpServletRequest request) {
        return ApiResponse.ok(assembler.detail(service.getPublicVideo(videoId, actor.optionalUserId())), traceId(request));
    }

    private String traceId(HttpServletRequest request) { return (String) request.getAttribute(TraceIdFilter.ATTRIBUTE); }
}
