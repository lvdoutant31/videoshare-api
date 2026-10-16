package com.yingliu.api.admin.moderation;

import com.yingliu.api.admin.log.OperLog;
import com.yingliu.api.admin.content.shared.PageResponse;
import com.yingliu.api.admin.security.AdminActorProvider;
import com.yingliu.common.api.ApiResponse;
import com.yingliu.framework.web.TraceIdFilter;
import com.yingliu.service.admin.AdminModerationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 旧评论治理适配，保留源码兼容；实际路由已由 ModerationController 提供。 */
// @RestController
@Validated
@RequestMapping("/api/v1/admin/comments")
public class AdminCommentController {
    private final AdminModerationService service;
    private final AdminActorProvider actor;
    public AdminCommentController(AdminModerationService service, AdminActorProvider actor) { this.service = service; this.actor = actor; }

    @GetMapping
    public ApiResponse<PageResponse<AdminModerationService.CommentItem>> list(@RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize, @RequestParam(required = false) String status, HttpServletRequest request) {
        var result = service.comments(page, pageSize, status);
        return ApiResponse.ok(new PageResponse<>(result.items(), page, pageSize, result.total()), traceId(request));
    }
    @PostMapping("/{id}/hide")
    @OperLog(module = "MODERATION", action = "HIDE", targetType = "COMMENT", detail = "隐藏评论")
    public ApiResponse<AdminModerationService.CommentDetail> hide(@PathVariable long id, @RequestBody(required = false) Reason body, HttpServletRequest request) { return change(id, "HIDDEN", body, request); }
    @PostMapping("/{id}/restore")
    @OperLog(module = "MODERATION", action = "RESTORE", targetType = "COMMENT", detail = "恢复评论")
    public ApiResponse<AdminModerationService.CommentDetail> restore(@PathVariable long id, @RequestBody(required = false) Reason body, HttpServletRequest request) { return change(id, "NORMAL", body, request); }
    @DeleteMapping("/{id}")
    @OperLog(module = "MODERATION", action = "DELETE", targetType = "COMMENT")
    public ApiResponse<AdminModerationService.CommentDetail> delete(@PathVariable long id, @RequestBody(required = false) Reason body, HttpServletRequest request) { return change(id, "DELETED", body, request); }
    @PostMapping("/batch")
    @OperLog(module = "MODERATION", action = "BATCH", targetType = "COMMENT", detail = "批量处置评论")
    public ApiResponse<Void> batch(@Valid @RequestBody BatchCommand body, HttpServletRequest request) {
        service.batchModerateComments(body.ids(), actor.currentAdminId(), body.status(), body.reason());
        return ApiResponse.ok(null, traceId(request));
    }
    private ApiResponse<AdminModerationService.CommentDetail> change(long id, String status, Reason body, HttpServletRequest request) {
        return ApiResponse.ok(service.moderateComment(id, actor.currentAdminId(), status, body == null ? null : body.reason()), traceId(request));
    }
    private String traceId(HttpServletRequest request) { return (String) request.getAttribute(TraceIdFilter.ATTRIBUTE); }
    public record Reason(String reason) { }
    public record BatchCommand(@NotEmpty List<Long> ids, String status, String reason) { }
}
