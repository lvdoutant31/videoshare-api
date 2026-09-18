package com.yingliu.api.app.share;

import com.yingliu.api.app.content.AppFeedAssembler;
import com.yingliu.api.app.content.video.AppVideoDtos;
import com.yingliu.api.app.security.AppActorProvider;
import com.yingliu.common.api.ApiResponse;
import com.yingliu.framework.web.TraceIdFilter;
import com.yingliu.service.app.AppShareService;
import com.yingliu.service.content.video.VideoAppService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

/**
 * App 域站内转发与分享事件（§2.5）。
 */
@RestController
@ConditionalOnBean(AppShareService.class)
@RequestMapping("/api/v1/app")
public class AppShareController {
    private final AppShareService service;
    private final VideoAppService videoService;
    private final AppActorProvider actor;
    private final AppFeedAssembler assembler;

    public AppShareController(AppShareService service, VideoAppService videoService,
                              AppActorProvider actor, AppFeedAssembler assembler) {
        this.service = service;
        this.videoService = videoService;
        this.actor = actor;
        this.assembler = assembler;
    }

    @GetMapping("/share-recipients")
    public ApiResponse<List<AppShareService.RecipientView>> recipients(@RequestParam(defaultValue = "") String q,
                                                                       @RequestParam(defaultValue = "20") int limit,
                                                                       HttpServletRequest request) {
        return ApiResponse.ok(service.searchRecipients(q, limit), traceId(request));
    }

    @GetMapping("/share-recipients/recent")
    public ApiResponse<List<AppShareService.RecipientView>> recentRecipients(@RequestParam(defaultValue = "20") int limit,
                                                                            HttpServletRequest request) {
        return ApiResponse.ok(service.recentRecipients(actor.currentUserId(), limit), traceId(request));
    }

    @PostMapping("/videos/{videoId}/shares/internal")
    public ApiResponse<InternalShareResponse> internalShare(@PathVariable long videoId,
                                                            @Valid @RequestBody InternalShareBody body,
                                                            HttpServletRequest request) {
        AppShareService.InternalShareResult result = service.sendInternal(actor.currentUserId(), videoId, body.recipientIds());
        List<InternalShareResponse.Item> items = result.results().stream()
                .map(r -> new InternalShareResponse.Item(r.recipientId(), r.success(), r.inboxId(), r.code()))
                .toList();
        return ApiResponse.ok(new InternalShareResponse(result.batchId(), items), traceId(request));
    }

    @GetMapping("/share-inbox")
    public ApiResponse<List<InboxItemDto>> inbox(HttpServletRequest request) {
        long me = actor.currentUserId();
        return ApiResponse.ok(service.inbox(me).stream().map(v -> toDto(v, me)).filter(Objects::nonNull).toList(), traceId(request));
    }

    @GetMapping("/share-inbox/{inboxId}")
    public ApiResponse<InboxItemDto> inboxDetail(@PathVariable long inboxId, HttpServletRequest request) {
        long me = actor.currentUserId();
        return ApiResponse.ok(toDto(service.inboxDetail(me, inboxId), me), traceId(request));
    }

    @PostMapping("/videos/{videoId}/share-events")
    public ApiResponse<Void> shareEvent(@PathVariable long videoId, @Valid @RequestBody ShareEventBody body,
                                        HttpServletRequest request) {
        service.recordShareEvent(actor.optionalUserId(), videoId, body.channel(), body.eventType(),
                (String) request.getAttribute(TraceIdFilter.ATTRIBUTE));
        return ApiResponse.ok(null, traceId(request));
    }

    private InboxItemDto toDto(AppShareService.InboxView view, long viewerId) {
        long videoId = Long.parseLong(view.videoId());
        long senderId = Long.parseLong(view.senderId());
        AppVideoDtos.Author sender = assembler.author(senderId, view.senderNickname(), false);
        try {
            AppVideoDtos.FeedItem video = assembler.feedItem(toFeed(videoService.getPublicVideo(videoId, viewerId)));
            return new InboxItemDto(view.id(), sender, video, view.status(), view.sentAt(), view.openedAt());
        } catch (Exception e) {
            // 视频已失效：仍返回卡片（INVALID 状态）
            return new InboxItemDto(view.id(), sender, null, view.status(), view.sentAt(), view.openedAt());
        }
    }

    private VideoAppService.FeedItem toFeed(VideoAppService.VideoDetail detail) {
        return new VideoAppService.FeedItem(detail.id(), detail.authorId(), detail.authorNickname(), detail.title(),
                detail.description(), detail.durationMs(), detail.videoUrl(), detail.cover(), detail.tags(), detail.stats(),
                detail.publishedAt(), detail.viewerState(), detail.authorFollowing());
    }

    private String traceId(HttpServletRequest request) {
        return (String) request.getAttribute(TraceIdFilter.ATTRIBUTE);
    }

    // ---- DTO ----

    public record InternalShareBody(@NotEmpty @Size(max = 20) List<String> recipientIds) { }
    public record InternalShareResponse(String batchId, List<Item> results) {
        public record Item(String recipientId, boolean success, String inboxId, String code) { }
    }
    public record InboxItemDto(String id, AppVideoDtos.Author sender, AppVideoDtos.FeedItem video,
                               String status, String sentAt, String openedAt) { }
    public record ShareEventBody(@NotBlank String channel, @NotBlank String eventType) { }
}
