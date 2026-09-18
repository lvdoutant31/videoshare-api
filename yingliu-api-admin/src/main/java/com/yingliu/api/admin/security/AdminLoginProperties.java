package com.yingliu.api.admin.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 管理端登录风控配置：失败锁定与可选的 IP 限流。 */
@Component
@ConfigurationProperties(prefix = "yingliu.auth.login")
public class AdminLoginProperties {
    /** 连续登录失败达到该次数后锁定账号。 */
    private int maxFailures = 5;

    /** 失败计数窗口与锁定时长，单位秒。 */
    private int lockSeconds = 900;

    /**
     * 单 IP 每分钟登录尝试上限；0 表示关闭。
     *
     * <p>默认关闭的原因：反向代理之后 {@code getRemoteAddr()} 只会返回代理地址，
     * 开启后所有用户共用一个计数器，反而会把正常用户一起限流。
     * 部署在 Nginx 之后并已配置 {@code server.forward-headers-strategy=native}
     * 使容器还原真实客户端 IP 时，才应把该值设为正数。
     */
    private int ipLimitPerMinute = 0;

    public int getMaxFailures() {
        return maxFailures;
    }

    public void setMaxFailures(int maxFailures) {
        this.maxFailures = maxFailures;
    }

    public int getLockSeconds() {
        return lockSeconds;
    }

    public void setLockSeconds(int lockSeconds) {
        this.lockSeconds = lockSeconds;
    }

    public int getIpLimitPerMinute() {
        return ipLimitPerMinute;
    }

    public void setIpLimitPerMinute(int ipLimitPerMinute) {
        this.ipLimitPerMinute = ipLimitPerMinute;
    }
}
