package com.yingliu.api.admin.moderation;

import com.yingliu.api.admin.content.shared.PageResponse;
import com.yingliu.api.admin.security.AdminPermissionAuthorizer;
import com.yingliu.common.api.ApiResponse;
import com.yingliu.framework.web.TraceIdFilter;
import com.yingliu.service.admin.AdminModerationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/** 管理端内容治理入口，统一返回治理查询及处置结果。 */
@RestController
@Validated
@RequestMapping("/api/v1/admin/moderation")
public class AdminModerationController {
    private final AdminModerationService service;
    private final AdminPermissionAuthorizer authorizer;

    public AdminModerationController(AdminModerationService service, AdminPermissionAuthorizer authorizer) {
        this.service = service;
        this.authorizer = authorizer;
    }

    @GetMapping("/comments")
    public ApiResponse<PageResponse<AdminModerationService.CommentItem>> comments(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize,
            @RequestParam(required = false) String status, HttpServletRequest request) {
        authorizer.require("comment:view");
        var result = service.comments(page, pageSize, status);
        return ApiResponse.ok(new PageResponse<>(result.items(), page, pageSize, result.total()), traceId(request));
    }

    @PostMapping("/comments/{id}")
    public ApiResponse<Void> moderateComment(@PathVariable long id, @Valid @RequestBody CommentCommand command,
                                               HttpServletRequest request) {
        service.moderateComment(id, authorizer.require("comment:moderate"), command.status(), command.reason());
        return ApiResponse.ok(null, traceId(request));
    }

    @GetMapping("/reports")
    public ApiResponse<PageResponse<AdminModerationService.ReportItem>> reports(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize,
            @RequestParam(required = false) String status, HttpServletRequest request) {
        authorizer.require("report:view");
        var result = service.reports(page, pageSize, status);
        return ApiResponse.ok(new PageResponse<>(result.items(), page, pageSize, result.total()), traceId(request));
    }

    @PostMapping("/reports/{id}/resolve")
    public ApiResponse<Void> resolveReport(@PathVariable long id, @Valid @RequestBody ResolveCommand command,
                                            HttpServletRequest request) {
        service.resolveReport(id, authorizer.require("report:resolve"), command.resolution(), command.note());
        return ApiResponse.ok(null, traceId(request));
    }

    private String traceId(HttpServletRequest request) {
        return (String) request.getAttribute(TraceIdFilter.ATTRIBUTE);
    }

    public record CommentCommand(@NotBlank String status, String reason) { }
    public record ResolveCommand(@NotBlank String resolution, String note) { }
}
