package com.yingliu.service.app.support;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Redis 实现（local/prod 环境）；Redis 不可用时由内存实现兜底 */
@Component
@ConditionalOnBean(StringRedisTemplate.class)
public class RedisVerificationCodeStore implements VerificationCodeStore {
    private final StringRedisTemplate redis;

    public RedisVerificationCodeStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void set(String key, String value, long ttlSeconds) {
        redis.opsForValue().set(key, value, Duration.ofSeconds(ttlSeconds));
    }

    @Override
    public String get(String key) {
        return redis.opsForValue().get(key);
    }

    @Override
    public void delete(String key) {
        redis.delete(key);
    }

    @Override
    public String consume(String key) {
        return redis.opsForValue().getAndDelete(key);
    }

    @Override
    public long increment(String key, long ttlSeconds) {
        Long value = redis.opsForValue().increment(key);
        redis.expire(key, Duration.ofSeconds(ttlSeconds));
        return value == null ? 0 : value;
    }
}
