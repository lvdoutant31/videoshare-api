package com.yingliu.api.admin.moderation;

import com.yingliu.api.admin.content.shared.PageResponse;
import com.yingliu.api.admin.security.AdminActorProvider;
import com.yingliu.common.api.ApiResponse;
import com.yingliu.common.web.TraceIdFilter;
import com.yingliu.service.admin.AdminModerationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

// Superseded by ModerationController; retained for source compatibility.
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
    @PostMapping("/{id}/claim") public ApiResponse<AdminModerationService.ReportDetail> claim(@PathVariable long id, HttpServletRequest request) {
        return ApiResponse.ok(service.claimReport(id, actor.currentAdminId()), traceId(request));
    }
    @PostMapping("/{id}/resolve") public ApiResponse<AdminModerationService.ReportDetail> resolve(@PathVariable long id, @RequestBody(required = false) Note body, HttpServletRequest request) {
        return ApiResponse.ok(service.resolveReport(id, actor.currentAdminId(), "RESOLVED", body == null ? null : body.note()), traceId(request));
    }
    @PostMapping("/{id}/reject") public ApiResponse<AdminModerationService.ReportDetail> reject(@PathVariable long id, @RequestBody(required = false) Note body, HttpServletRequest request) {
        return ApiResponse.ok(service.rejectReport(id, actor.currentAdminId(), body == null ? null : body.note()), traceId(request));
    }
    private String traceId(HttpServletRequest request) { return (String) request.getAttribute(TraceIdFilter.ATTRIBUTE); }
    public record Note(String note) { }
}
