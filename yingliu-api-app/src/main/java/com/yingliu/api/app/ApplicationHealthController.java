package com.yingliu.api.app;

import com.yingliu.common.api.ApiResponse;
import com.yingliu.common.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class ApplicationHealthController {
    @GetMapping({"/health", "/api/v1/app/health"})
    public ApiResponse<Map<String, String>> health(HttpServletRequest request) {
        String traceId = (String) request.getAttribute(TraceIdFilter.ATTRIBUTE);
        return ApiResponse.ok(Map.of("service", "app-api", "status", "UP"), traceId);
    }
}
