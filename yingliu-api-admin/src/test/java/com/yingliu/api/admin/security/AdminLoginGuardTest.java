package com.yingliu.api.admin.security;

import com.yingliu.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 登录风控契约：失败累计、锁定、成功清零与可选的 IP 限流。 */
class AdminLoginGuardTest {

    @Test
    void locksAccountOnceFailuresReachThreshold() {
        AdminLoginProperties properties = new AdminLoginProperties();
        properties.setMaxFailures(3);
        AdminLoginGuard guard = new AdminLoginGuard(new FakeTtlStore(), properties);

        guard.onFailure("admin");
        guard.onFailure("admin");
        assertDoesNotThrow(() -> guard.beforeAttempt("admin", "10.0.0.1"), "未达阈值不应锁定");

        guard.onFailure("admin");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> guard.beforeAttempt("admin", "10.0.0.1"));
        assertEquals("ACCOUNT_LOCKED", exception.getCode());
        assertEquals(HttpStatus.LOCKED, exception.getStatus());
    }

    @Test
    void lockOnlyAffectsTheFailingAccount() {
        AdminLoginProperties properties = new AdminLoginProperties();
        properties.setMaxFailures(1);
        AdminLoginGuard guard = new AdminLoginGuard(new FakeTtlStore(), properties);

        guard.onFailure("admin");

        assertThrows(BusinessException.class, () -> guard.beforeAttempt("admin", "10.0.0.1"));
        assertDoesNotThrow(() -> guard.beforeAttempt("other", "10.0.0.1"));
    }

    @Test
    void successfulLoginClearsFailureState() {
        AdminLoginProperties properties = new AdminLoginProperties();
        properties.setMaxFailures(2);
        AdminLoginGuard guard = new AdminLoginGuard(new FakeTtlStore(), properties);

        guard.onFailure("admin");
        guard.onSuccess("admin");
        guard.onFailure("admin");

        assertDoesNotThrow(() -> guard.beforeAttempt("admin", "10.0.0.1"), "成功登录后计数应清零");
    }

    @Test
    void ignoresUsernameCaseAndPaddingWhenCounting() {
        AdminLoginProperties properties = new AdminLoginProperties();
        properties.setMaxFailures(2);
        AdminLoginGuard guard = new AdminLoginGuard(new FakeTtlStore(), properties);

        guard.onFailure(" admin ");
        guard.onFailure("admin");

        assertThrows(BusinessException.class, () -> guard.beforeAttempt("admin", "10.0.0.1"));
    }

    @Test
    void ipLimitIsDisabledByDefault() {
        AdminLoginGuard guard = new AdminLoginGuard(new FakeTtlStore(), new AdminLoginProperties());

        // 默认关闭的原因：反向代理后 getRemoteAddr() 只返回代理地址，
        // 开启会让所有用户共用一个计数器而被一起限流。
        for (int attempt = 0; attempt < 50; attempt++) {
            assertDoesNotThrow(() -> guard.beforeAttempt("admin", "10.0.0.1"));
        }
    }

    @Test
    void appliesIpLimitWhenExplicitlyEnabled() {
        AdminLoginProperties properties = new AdminLoginProperties();
        properties.setIpLimitPerMinute(2);
        AdminLoginGuard guard = new AdminLoginGuard(new FakeTtlStore(), properties);

        guard.beforeAttempt("admin", "10.0.0.1");
        guard.beforeAttempt("admin", "10.0.0.1");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> guard.beforeAttempt("admin", "10.0.0.1"));
        assertEquals("RATE_LIMITED", exception.getCode());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exception.getStatus());
    }
}
