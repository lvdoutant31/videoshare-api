package com.yingliu.api.admin.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 管理端跨域配置；允许来源由部署配置提供，保持原请求凭据策略。 */
@Configuration
public class AdminCorsConfiguration implements WebMvcConfigurer {
    private final String allowedOrigin;

    public AdminCorsConfiguration(@Value("${yingliu.admin.cors.allowed-origin:}") String allowedOrigin) {
        this.allowedOrigin = allowedOrigin;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (allowedOrigin == null || allowedOrigin.isBlank()) return;
        registry.addMapping("/api/v1/admin/**")
                .allowedOrigins(allowedOrigin)
                // 管理端大量使用 PATCH/PUT/DELETE（编辑、下架、删除等）。此前只放行 GET/POST/OPTIONS，
                // 跨域部署时这些请求会在预检阶段被拒绝，表现为"编辑和删除全部失败"。
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", "X-Trace-Id", "Idempotency-Key",
                        "X-Yingliu-Encrypted", "X-Yingliu-Envelope")
                .exposedHeaders("X-Trace-Id")
                // Cookie 会话必须携带凭证；因此来源不能使用通配符。
                .allowCredentials(true)
                .maxAge(3600);
    }
}
