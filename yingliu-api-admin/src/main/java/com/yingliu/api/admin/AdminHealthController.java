package com.yingliu.api.admin;

import com.yingliu.common.api.ApiResponse;
import com.yingliu.common.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminHealthController {
    @GetMapping("/health")
    public ApiResponse<Map<String, String>> health(HttpServletRequest request) {
        String traceId = (String) request.getAttribute(TraceIdFilter.ATTRIBUTE);
        return ApiResponse.ok(Map.of("service", "admin-api", "status", "UP"), traceId);
    }
}
