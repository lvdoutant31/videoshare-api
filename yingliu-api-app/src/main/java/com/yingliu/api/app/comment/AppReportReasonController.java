package com.yingliu.api.app.comment;

import com.yingliu.common.api.ApiResponse;
import com.yingliu.framework.web.TraceIdFilter;
import com.yingliu.service.app.AppReportReasonService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@ConditionalOnBean(AppReportReasonService.class)
@RequestMapping("/api/v1/app/report-reasons")
public class AppReportReasonController {
    private final AppReportReasonService service;

    public AppReportReasonController(AppReportReasonService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<AppReportReasonService.ReportReason>> reasons(
            @RequestParam AppReportReasonService.TargetType targetType, HttpServletRequest request) {
        return ApiResponse.ok(service.list(targetType), (String) request.getAttribute(TraceIdFilter.ATTRIBUTE));
    }
}
