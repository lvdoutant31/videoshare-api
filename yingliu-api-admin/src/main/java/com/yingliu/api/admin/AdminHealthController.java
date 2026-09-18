package com.yingliu.api.admin;

import com.yingliu.common.api.ApiResponse;
import com.yingliu.common.security.PublicApi;
import com.yingliu.framework.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 管理端健康探针，用于验证统一启动模块已装配管理端路由。 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminHealthController {
    /** 探活接口供监控与编排系统调用，不携带凭证，因此显式声明为公开接口。 */
    @PublicApi
    @GetMapping("/health")
    public ApiResponse<Map<String, String>> health(HttpServletRequest request) {
        String traceId = (String) request.getAttribute(TraceIdFilter.ATTRIBUTE);
        return ApiResponse.ok(Map.of("service", "admin-api", "status", "UP"), traceId);
    }
}
