package com.yingliu.api.admin.moderation;

import com.yingliu.api.admin.log.OperLog;
import com.yingliu.api.admin.content.shared.PageResponse;
import com.yingliu.api.admin.security.AdminActorProvider;
import com.yingliu.common.api.ApiResponse;
import com.yingliu.framework.web.TraceIdFilter;
import com.yingliu.service.admin.AdminModerationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

/** 旧举报管理适配，保留源码兼容；实际路由已由 ModerationController 提供。 */
// @RestController
@RequestMapping("/api/v1/admin/reports")
public class AdminReportController {
    private final AdminModerationService service;
    private final AdminActorProvider actor;
    public AdminReportController(AdminModerationService service, AdminActorProvider actor) { this.service = service; this.actor = actor; }

    @GetMapping
    public ApiResponse<PageResponse<AdminModerationService.ReportItem>> list(@RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize, @RequestParam(required = false) String status, HttpServletRequest request) {
        var result = service.reports(page, pageSize, status);
        return ApiResponse.ok(new PageResponse<>(result.items(), page, pageSize, result.total()), traceId(request));
    }
    @PostMapping("/{id}/claim")
    @OperLog(module = "MODERATION", action = "CLAIM", targetType = "REPORT", detail = "认领举报")
    public ApiResponse<AdminModerationService.ReportDetail> claim(@PathVariable long id, HttpServletRequest request) {
        return ApiResponse.ok(service.claimReport(id, actor.currentAdminId()), traceId(request));
    }
    @PostMapping("/{id}/resolve")
    @OperLog(module = "MODERATION", action = "RESOLVE", targetType = "REPORT", detail = "解决举报")
    public ApiResponse<AdminModerationService.ReportDetail> resolve(@PathVariable long id, @RequestBody(required = false) Note body, HttpServletRequest request) {
        return ApiResponse.ok(service.resolveReport(id, actor.currentAdminId(), "RESOLVED", body == null ? null : body.note()), traceId(request));
    }
    @PostMapping("/{id}/reject")
    @OperLog(module = "MODERATION", action = "REJECT", targetType = "REPORT", detail = "驳回举报")
    public ApiResponse<AdminModerationService.ReportDetail> reject(@PathVariable long id, @RequestBody(required = false) Note body, HttpServletRequest request) {
        return ApiResponse.ok(service.rejectReport(id, actor.currentAdminId(), body == null ? null : body.note()), traceId(request));
    }
    private String traceId(HttpServletRequest request) { return (String) request.getAttribute(TraceIdFilter.ATTRIBUTE); }
    public record Note(String note) { }
}
