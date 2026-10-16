package com.yingliu.api.admin.security;

import com.yingliu.common.exception.BusinessException;
import com.yingliu.service.support.TtlStore;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 管理端登录风控：按账号累计连续失败次数并锁定，可选按 IP 限流。
 *
 * <p>计数与锁定标记都存放在带 TTL 的 {@link TtlStore} 中，因此多实例部署下计数一致，
 * 且无需为"临时锁定"引入数据库表。
 */
@Component
public class AdminLoginGuard {
    private static final String FAILURE_KEY = "yingliu:auth:login:fail:%s";
    private static final String LOCK_KEY = "yingliu:auth:login:lock:%s";
    private static final String IP_KEY = "yingliu:auth:login:ip:%s";
    private static final long IP_WINDOW_SECONDS = 60;

    private final TtlStore store;
    private final AdminLoginProperties properties;

    public AdminLoginGuard(TtlStore store, AdminLoginProperties properties) {
        this.store = store;
        this.properties = properties;
    }

    /**
     * 校验密码前调用。账号处于锁定状态或来源 IP 超过限额时抛出业务异常，
     * 使撞库尝试在触达密码校验前就被拒绝。
     */
    public void beforeAttempt(String username, String clientIp) {
        String account = normalize(username);
        if (store.get(LOCK_KEY.formatted(account)) != null) {
            throw new BusinessException("ACCOUNT_LOCKED", HttpStatus.LOCKED,
                    "登录失败次数过多，账号已临时锁定，请稍后再试");
        }
        if (properties.getIpLimitPerMinute() > 0 && clientIp != null && !clientIp.isBlank()) {
            long attempts = store.increment(IP_KEY.formatted(clientIp), IP_WINDOW_SECONDS);
            if (attempts > properties.getIpLimitPerMinute()) {
                throw new BusinessException("RATE_LIMITED", HttpStatus.TOO_MANY_REQUESTS, "登录请求过于频繁，请稍后再试");
            }
        }
    }

    /** 密码校验失败后调用；达到阈值时写入锁定标记。 */
    public void onFailure(String username) {
        String account = normalize(username);
        long failures = store.increment(FAILURE_KEY.formatted(account), properties.getLockSeconds());
        if (failures >= properties.getMaxFailures()) {
            store.set(LOCK_KEY.formatted(account), "1", properties.getLockSeconds());
        }
    }

    /** 登录成功后调用：清除该账号的失败计数与锁定标记。 */
    public void onSuccess(String username) {
        String account = normalize(username);
        store.delete(FAILURE_KEY.formatted(account));
        store.delete(LOCK_KEY.formatted(account));
    }

    /** 用户名在登录用例中已 trim；这里统一处理，避免大小写或空白产生不同的计数键。 */
    private String normalize(String username) {
        return username == null ? "" : username.trim();
    }
}
