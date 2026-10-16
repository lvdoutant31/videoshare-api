package com.yingliu.api.app.interaction;

import com.yingliu.api.app.content.AppFeedAssembler;
import com.yingliu.api.app.content.video.AppVideoDtos;
import com.yingliu.api.app.security.AppActorProvider;
import com.yingliu.common.api.ApiResponse;
import com.yingliu.framework.web.TraceIdFilter;
import com.yingliu.service.app.AppInteractionService;
import com.yingliu.service.content.shared.CursorPage;
import com.yingliu.service.content.video.VideoAppService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * App 域互动（§2.3）：点赞/收藏/关注目标状态幂等；个人列表；作者主页与作品。
 */
@RestController
@org.springframework.validation.annotation.Validated
@ConditionalOnBean(AppInteractionService.class)
@RequestMapping("/api/v1/app")
public class AppInteractionController {
    private final AppInteractionService service;
    private final VideoAppService videoService;
    private final AppActorProvider actor;
    private final AppFeedAssembler assembler;

    public AppInteractionController(AppInteractionService service, VideoAppService videoService,
                                    AppActorProvider actor, AppFeedAssembler assembler) {
        this.service = service;
        this.videoService = videoService;
        this.actor = actor;
        this.assembler = assembler;
    }

    @PutMapping("/videos/{videoId}/like")
    public ApiResponse<StateResponse> like(@PathVariable long videoId, @Valid @RequestBody ActiveBody body,
                                           HttpServletRequest request) {
        return ApiResponse.ok(state(service.setVideoLike(actor.currentUserId(), videoId, body.active())), traceId(request));
    }

    @PutMapping("/videos/{videoId}/favorite")
    public ApiResponse<StateResponse> favorite(@PathVariable long videoId, @Valid @RequestBody ActiveBody body,
                                               HttpServletRequest request) {
        return ApiResponse.ok(state(service.setVideoFavorite(actor.currentUserId(), videoId, body.active())), traceId(request));
    }

    @PutMapping("/users/{userId}/follow")
    public ApiResponse<StateResponse> follow(@PathVariable long userId, @Valid @RequestBody ActiveBody body,
                                             HttpServletRequest request) {
        return ApiResponse.ok(state(service.setFollow(actor.currentUserId(), userId, body.active())), traceId(request));
    }

    @GetMapping("/me/favorites")
    public ApiResponse<AppVideoDtos.CursorPage> favorites(@RequestParam(required = false) String cursor,
                                                           @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit,
                                                           HttpServletRequest request) {
        long me = actor.currentUserId();
        return ApiResponse.ok(assemblePage(service.listFavoriteVideoIds(me, cursor, limit)), traceId(request));
    }

    @GetMapping("/me/likes")
    public ApiResponse<AppVideoDtos.CursorPage> likes(@RequestParam(required = false) String cursor,
                                                       @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit,
                                                       HttpServletRequest request) {
        long me = actor.currentUserId();
        return ApiResponse.ok(assemblePage(service.listLikeVideoIds(me, cursor, limit)), traceId(request));
    }

    @GetMapping("/me/following")

    public ApiResponse<List<AppInteractionService.UserBriefView>> following(HttpServletRequest request) {
        return ApiResponse.ok(service.listFollowing(actor.currentUserId()), traceId(request));
    }

    @GetMapping("/me/followers")
    public ApiResponse<List<AppInteractionService.UserBriefView>> followers(HttpServletRequest request) {
        return ApiResponse.ok(service.listFollowers(actor.currentUserId()), traceId(request));
    }

    @GetMapping("/users/{userId}")
    public ApiResponse<AppInteractionService.AuthorProfileView> authorProfile(@PathVariable long userId,
                                                                              HttpServletRequest request) {
        return ApiResponse.ok(service.getAuthorProfile(userId, actor.optionalUserId()), traceId(request));
    }

    @GetMapping("/users/{userId}/videos")
    public ApiResponse<AppVideoDtos.CursorPage> authorVideos(@PathVariable long userId,
                                                              @RequestParam(required = false) String cursor,
                                                              @RequestParam(defaultValue = "9") @Min(1) @Max(50) int limit,
                                                              HttpServletRequest request) {
        Long viewerId = actor.optionalUserId();
        return ApiResponse.ok(assemblePage(service.listAuthorVideoIds(userId, cursor, limit), viewerId), traceId(request));
    }

    // ---- 组装 ----

    @GetMapping("/users")
    public ApiResponse<CursorPage<AppInteractionService.AuthorSearchView>> authors(
            @RequestParam String keyword, @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit, HttpServletRequest request) {
        return ApiResponse.ok(service.searchAuthors(keyword, cursor, limit, actor.optionalUserId()), traceId(request));
    }

    @GetMapping("/users/{userId}/likes")
    public ApiResponse<AppVideoDtos.CursorPage> authorLikes(@PathVariable long userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit, HttpServletRequest request) {
        Long viewerId = actor.optionalUserId();
        service.getAuthorProfile(userId, viewerId);
        return ApiResponse.ok(assemblePage(service.listLikeVideoIds(userId, cursor, limit), viewerId), traceId(request));
    }

    private AppVideoDtos.CursorPage assemblePage(CursorPage<String> ids) {
        return assemblePage(ids, actor.optionalUserId());
    }

    private AppVideoDtos.CursorPage assemblePage(CursorPage<String> ids, Long viewerId) {
        List<AppVideoDtos.FeedItem> items = ids.items().stream()
                .map(id -> {
                    try {
                        return assembler.feedItem(toFeed(videoService.getPublicVideo(Long.parseLong(id), viewerId)));
                    } catch (Exception e) {
                        return null; // 视频中途下架/删除则跳过
                    }
                })
                .filter(java.util.Objects::nonNull)
                .toList();
        return new AppVideoDtos.CursorPage(items, ids.nextCursor());
    }

    private VideoAppService.FeedItem toFeed(VideoAppService.VideoDetail detail) {
        return new VideoAppService.FeedItem(detail.id(), detail.authorId(), detail.authorNickname(), detail.title(),
                detail.description(), detail.durationMs(), detail.videoUrl(), detail.cover(), detail.categories(), detail.tags(), detail.stats(),
                detail.publishedAt(), detail.viewerState(), detail.authorFollowing());
    }

    private StateResponse state(AppInteractionService.TargetState value) {
        return new StateResponse(value.active(), value.count());
    }

    private String traceId(HttpServletRequest request) {
        return (String) request.getAttribute(TraceIdFilter.ATTRIBUTE);
    }

    // ---- DTO ----

    public record ActiveBody(@NotNull Boolean active) { }
    public record StateResponse(boolean active, long count) { }
}
