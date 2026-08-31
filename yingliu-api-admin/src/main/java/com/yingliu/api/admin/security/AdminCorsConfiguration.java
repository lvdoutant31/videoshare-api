package com.yingliu.api.admin.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

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
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("Content-Type", "X-Trace-Id")
                .exposedHeaders("X-Trace-Id")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
