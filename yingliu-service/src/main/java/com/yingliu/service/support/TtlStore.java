package com.yingliu.service.support;

import com.yingliu.service.app.support.VerificationCodeStore;

/**
 * 带过期时间的键值存储（生产为 Redis，无 Redis 时为进程内存）。
 *
 * <p>承载短生命周期、可丢失、可重建的状态：验证码、企微授权 state、
 * 管理端登录失败计数与授权关系缓存。写入必须给出 TTL，不存放需要持久化的业务数据。
 *
 * @see VerificationCodeStore 验证码与授权 state 的既有用法
 */
public interface TtlStore {

    void set(String key, String value, long ttlSeconds);

    String get(String key);

    void delete(String key);

    /** 原子取出并删除，保证同一键值不能被并发重放。 */
    String consume(String key);

    /** 原子递增并设置 TTL；返回递增后的值（计数与限流用）。 */
    long increment(String key, long ttlSeconds);
}
