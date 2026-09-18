package com.yingliu.framework.web;

import cn.dev33.satoken.exception.DisableServiceException;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.stp.StpUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 鉴权异常到 401/403 稳定错误码的契约测试。
 *
 * <p>这些错误码是前端的判定依据：401 触发跳转登录页，403 只提示无权限。
 * 若映射退化为 500，前端将无法识别会话过期。
 */
class AuthExceptionAdviceTest {
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new AuthExceptionAdvice())
                .addFilters(new TraceIdFilter())
                .build();
    }

    @Test
    void treatsMissingCredentialAsNotLoggedIn() throws Exception {
        mockMvc.perform(get("/test/not-token").header(TraceIdFilter.HEADER, "trace-auth-001"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"))
                .andExpect(jsonPath("$.message").value("请先登录"))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.traceId").value("trace-auth-001"));
    }

    @Test
    void treatsMalformedCredentialAsNotLoggedIn() throws Exception {
        mockMvc.perform(get("/test/no-prefix"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REQUIRED"));
    }

    @Test
    void reportsExpiredSessionWithTokenExpiredCode() throws Exception {
        mockMvc.perform(get("/test/timeout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"))
                .andExpect(jsonPath("$.message").value("登录已过期，请重新登录"));
    }

    @Test
    void reportsInvalidTokenAsExpiredSession() throws Exception {
        mockMvc.perform(get("/test/invalid"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"));
    }

    @Test
    void distinguishesReplacedAndKickedSessions() throws Exception {
        mockMvc.perform(get("/test/replaced"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"))
                .andExpect(jsonPath("$.message").value("账号已在其他设备登录"));

        mockMvc.perform(get("/test/kick-out"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"))
                .andExpect(jsonPath("$.message").value("账号已被强制下线"));
    }

    @Test
    void doesNotRedirectToLoginWhenOnlyPermissionIsMissing() throws Exception {
        mockMvc.perform(get("/test/no-permission"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"))
                .andExpect(jsonPath("$.message").value("无权执行此操作"));
    }

    @Test
    void reportsDisabledAccountAsForbidden() throws Exception {
        mockMvc.perform(get("/test/disabled"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

    @RestController
    static class ThrowingController {
        @GetMapping("/test/not-token")
        void notToken() {
            throw notLogin(NotLoginException.NOT_TOKEN, NotLoginException.NOT_TOKEN_MESSAGE);
        }

        @GetMapping("/test/no-prefix")
        void noPrefix() {
            throw notLogin(NotLoginException.NO_PREFIX, NotLoginException.NO_PREFIX_MESSAGE);
        }

        @GetMapping("/test/timeout")
        void timeout() {
            throw notLogin(NotLoginException.TOKEN_TIMEOUT, NotLoginException.TOKEN_TIMEOUT_MESSAGE);
        }

        @GetMapping("/test/invalid")
        void invalid() {
            throw notLogin(NotLoginException.INVALID_TOKEN, NotLoginException.INVALID_TOKEN_MESSAGE);
        }

        @GetMapping("/test/replaced")
        void replaced() {
            throw notLogin(NotLoginException.BE_REPLACED, NotLoginException.BE_REPLACED_MESSAGE);
        }

        @GetMapping("/test/kick-out")
        void kickOut() {
            throw notLogin(NotLoginException.KICK_OUT, NotLoginException.KICK_OUT_MESSAGE);
        }

        @GetMapping("/test/no-permission")
        void noPermission() {
            throw new NotPermissionException("video:view", StpUtil.TYPE);
        }

        @GetMapping("/test/disabled")
        void disabled() {
            // 构造顺序：loginType, loginId, service, level, limitLevel, disableTime。
            throw new DisableServiceException(StpUtil.TYPE, 9000001L, "comment", 1, 2, 300L);
        }

        /** Sa-Token 的构造顺序为 (message, loginType, type)。 */
        private static NotLoginException notLogin(String type, String message) {
            return new NotLoginException(message, StpUtil.TYPE, type);
        }
    }
}
