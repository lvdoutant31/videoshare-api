package com.yingliu.api.app;

import com.yingliu.common.api.ApiResponse;
import com.yingliu.framework.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 提供手机端与基础健康探针，无需访问数据库或 Redis。 */
@RestController
public class ApplicationHealthController {
    @GetMapping({"/health", "/api/v1/app/health"})
    public ApiResponse<Map<String, String>> health(HttpServletRequest request) {
        String traceId = (String) request.getAttribute(TraceIdFilter.ATTRIBUTE);
        return ApiResponse.ok(Map.of("service", "app-api", "status", "UP"), traceId);
    }
}
