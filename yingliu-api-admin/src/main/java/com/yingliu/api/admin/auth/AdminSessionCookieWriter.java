package com.yingliu.api.admin.auth;

import cn.dev33.satoken.config.SaCookieConfig;
import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Ensures a successful admin login always returns a browser-compatible session cookie. */
@Component
public class AdminSessionCookieWriter {
    private static final Logger log = LoggerFactory.getLogger(AdminSessionCookieWriter.class);

    public void write(HttpServletResponse response) {
        StpLogic logic = StpUtil.getStpLogic();
        SaTokenConfig config = logic.getConfigOrGlobal();
        String token = logic.getTokenValue();
        long timeout = logic.getTokenTimeout();
        ResponseCookie cookie = build(config, token, timeout);

        // Sa-Token normally adds this header during login. Set it explicitly so the final servlet
        // response remains correct even when an adapter or response wrapper drops that first header.
        response.setHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        log.info("Admin login cookie: result=issued name={} path={} secure={} sameSite={} maxAge={}",
                cookie.getName(), cookie.getPath(), cookie.isSecure(), cookie.getSameSite(), cookie.getMaxAge());
    }

    static ResponseCookie build(SaTokenConfig config, String token, long timeoutSeconds) {
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("Sa-Token did not create an admin session token");
        }
        SaCookieConfig source = config.getCookie();
        String path = source.getPath() == null || source.getPath().isBlank() ? "/" : source.getPath();
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(config.getTokenName(), token)
                .path(path)
                .httpOnly(Boolean.TRUE.equals(source.getHttpOnly()))
                .secure(Boolean.TRUE.equals(source.getSecure()));
        if (source.getDomain() != null && !source.getDomain().isBlank()) builder.domain(source.getDomain());
        if (source.getSameSite() != null && !source.getSameSite().isBlank()) builder.sameSite(source.getSameSite());
        if (timeoutSeconds > 0) builder.maxAge(Duration.ofSeconds(timeoutSeconds));
        return builder.build();
    }
}
