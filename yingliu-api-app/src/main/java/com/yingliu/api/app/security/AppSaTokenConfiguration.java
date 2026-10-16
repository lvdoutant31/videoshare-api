package com.yingliu.api.app.security;

import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.stp.StpLogic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * App 域 Sa-Token 会话（与 admin 域隔离）：
 * - 独立 loginType "app"（Redis key 天然隔离）
 * - 通过 Authorization 请求头读取 Bearer 令牌，不读取管理端 Cookie
 * - 会话时长由配置统一控制，Header 传递（Authorization: Bearer xxx）
 */
@Configuration
public class AppSaTokenConfiguration {

    public static final String APP_LOGIN_TYPE = "app";

    @Bean
    public AppActorProvider appActorProvider(
            @Value("${yingliu.app.session-timeout-seconds:604800}") long sessionTimeoutSeconds) {
        SaTokenConfig config = new SaTokenConfig();
        // 与前端契约一致：Authorization: Bearer <token>（tokenName 即 header 名，tokenPrefix 为 Bearer）
        config.setTokenName("Authorization");
        config.setTokenPrefix("Bearer");
        config.setTimeout(Math.max(3600, sessionTimeoutSeconds));
        config.setActiveTimeout(-1);
        config.setIsConcurrent(true);
        config.setIsShare(false);
        config.setTokenStyle("uuid");
        config.setIsReadHeader(true);
        config.setIsReadCookie(false);
        config.setIsReadBody(false);
        StpLogic logic = new StpLogic(APP_LOGIN_TYPE);
        logic.setConfig(config);
        // Sa-Token auto-injects any StpLogic bean into the global StpUtil.
        // Keep the app logic private to its actor provider so admin keeps its cookie configuration.
        return new AppActorProvider(logic);
    }
}
