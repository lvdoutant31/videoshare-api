package com.yingliu.api.admin.moderation;

import com.yingliu.api.admin.content.shared.PageResponse;
import com.yingliu.api.admin.security.AdminActorProvider;
import com.yingliu.common.api.ApiResponse;
import com.yingliu.common.web.TraceIdFilter;
import com.yingliu.service.admin.AdminModerationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/v1/admin/moderation")
public class AdminModerationController {
    private final AdminModerationService service;
    private final AdminActorProvider actor;

    public AdminModerationController(AdminModerationService service, AdminActorProvider actor) {
        this.service = service;
        this.actor = actor;
    }

    @GetMapping("/comments")
    public ApiResponse<PageResponse<AdminModerationService.CommentItem>> comments(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize,
            @RequestParam(required = false) String status, HttpServletRequest request) {
        var result = service.comments(page, pageSize, status);
        return ApiResponse.ok(new PageResponse<>(result.items(), page, pageSize, result.total()), traceId(request));
    }

    @PostMapping("/comments/{id}")
    public ApiResponse<Void> moderateComment(@PathVariable long id, @Valid @RequestBody CommentCommand command,
                                               HttpServletRequest request) {
        service.moderateComment(id, actor.currentAdminId(), command.status(), command.reason());
        return ApiResponse.ok(null, traceId(request));
    }

    @GetMapping("/reports")
    public ApiResponse<PageResponse<AdminModerationService.ReportItem>> reports(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize,
            @RequestParam(required = false) String status, HttpServletRequest request) {
        var result = service.reports(page, pageSize, status);
        return ApiResponse.ok(new PageResponse<>(result.items(), page, pageSize, result.total()), traceId(request));
    }

    @PostMapping("/reports/{id}/resolve")
    public ApiResponse<Void> resolveReport(@PathVariable long id, @Valid @RequestBody ResolveCommand command,
                                            HttpServletRequest request) {
        service.resolveReport(id, actor.currentAdminId(), command.resolution(), command.note());
        return ApiResponse.ok(null, traceId(request));
    }

    private String traceId(HttpServletRequest request) {
        return (String) request.getAttribute(TraceIdFilter.ATTRIBUTE);
    }

    public record CommentCommand(@NotBlank String status, String reason) { }
    public record ResolveCommand(@NotBlank String resolution, String note) { }
}
