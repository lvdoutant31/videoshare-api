package com.yingliu.api.app.comment;

import com.yingliu.api.app.security.AppActorProvider;
import com.yingliu.common.api.ApiResponse;
import com.yingliu.framework.web.TraceIdFilter;
import com.yingliu.service.app.AppCommentService;
import com.yingliu.service.content.shared.CursorPage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * App 域评论（§2.4）：列表/回复/发布/点赞/删除/举报。
 */
@RestController
@Validated
@ConditionalOnBean(AppCommentService.class)
@RequestMapping("/api/v1/app")
public class AppCommentController {
    private final AppCommentService service;
    private final AppActorProvider actor;

    public AppCommentController(AppCommentService service, AppActorProvider actor) {
        this.service = service;
        this.actor = actor;
    }

    @GetMapping("/videos/{videoId}/comments")
    public ApiResponse<CursorPage<AppCommentService.CommentView>> comments(@PathVariable long videoId,
                                                                            @RequestParam(defaultValue = "HOT") String sort,
                                                                            @RequestParam(required = false) String cursor,
                                                                            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit,
                                                                            HttpServletRequest request) {
        return ApiResponse.ok(service.listRootComments(videoId, sort, cursor, limit, actor.optionalUserId()), traceId(request));
    }

    @GetMapping("/comments/{commentId}/replies")
    public ApiResponse<List<AppCommentService.CommentView>> replies(@PathVariable long commentId, HttpServletRequest request) {
        return ApiResponse.ok(service.listReplies(commentId, actor.optionalUserId()), traceId(request));
    }

    @PostMapping("/videos/{videoId}/comments")
    public ApiResponse<AppCommentService.PublishResult> publish(@PathVariable long videoId,
                                                                @Valid @RequestBody PublishBody body,
                                                                HttpServletRequest request) {
        return ApiResponse.ok(service.publish(actor.currentUserId(), videoId, body.content(), body.replyToCommentId()), traceId(request));
    }

    @GetMapping("/comment-notifications")
    public ApiResponse<CursorPage<AppCommentService.ReplyNotificationView>> notifications(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit,
            HttpServletRequest request) {
        return ApiResponse.ok(service.listReplyNotifications(actor.currentUserId(), cursor, limit), traceId(request));
    }

    @PutMapping("/comment-notifications/{notificationId}/read")
    public ApiResponse<Void> readNotification(@PathVariable long notificationId, HttpServletRequest request) {
        service.markReplyNotificationRead(actor.currentUserId(), notificationId);
        return ApiResponse.ok(null, traceId(request));
    }

    @PutMapping("/comments/{commentId}/like")
    public ApiResponse<AppCommentService.TargetState> like(@PathVariable long commentId, @Valid @RequestBody ActiveBody body,
                                                           HttpServletRequest request) {
        return ApiResponse.ok(service.setCommentLike(actor.currentUserId(), commentId, body.active()), traceId(request));
    }

    @DeleteMapping("/comments/{commentId}")
    public ApiResponse<Void> delete(@PathVariable long commentId, HttpServletRequest request) {
        service.deleteOwned(actor.currentUserId(), commentId);
        return ApiResponse.ok(null, traceId(request));
    }

    @PostMapping("/comments/{commentId}/report")
    public ApiResponse<Void> reportComment(@PathVariable long commentId, @Valid @RequestBody ReportBody body,
                                           HttpServletRequest request) {
        service.report(actor.currentUserId(), commentId, body.reasonCode(), body.description());
        return ApiResponse.ok(null, traceId(request));
    }

    @PostMapping("/videos/{videoId}/report")
    public ApiResponse<Void> reportVideo(@PathVariable long videoId, @Valid @RequestBody ReportBody body,
                                         HttpServletRequest request) {
        // 视频举报：记录 target_snapshot 最小快照
        service.reportVideo(actor.currentUserId(), videoId, body.reasonCode(), body.description());
        return ApiResponse.ok(null, traceId(request));
    }

    @PostMapping("/users/{userId}/report")
    public ApiResponse<Void> reportUser(@PathVariable long userId, @Valid @RequestBody ReportBody body,
                                        HttpServletRequest request) {
        service.reportUser(actor.currentUserId(), userId, body.reasonCode(), body.description());
        return ApiResponse.ok(null, traceId(request));
    }

    private String traceId(HttpServletRequest request) {
        return (String) request.getAttribute(TraceIdFilter.ATTRIBUTE);
    }

    // ---- DTO ----

    public record PublishBody(@NotBlank @Size(max = 1000) String content, Long replyToCommentId) { }
    public record ActiveBody(Boolean active) { }
    public record ReportBody(@NotBlank String reasonCode, @Size(max = 1000) String description) { }
}
