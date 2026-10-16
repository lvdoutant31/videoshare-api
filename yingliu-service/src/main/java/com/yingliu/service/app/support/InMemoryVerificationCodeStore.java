package com.yingliu.service.app.support;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 内存实现（测试/无 Redis 环境），生产优先 Redis 实现 */
@Component
@ConditionalOnMissingBean(StringRedisTemplate.class)
public class InMemoryVerificationCodeStore implements VerificationCodeStore {
    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    private record Entry(String value, long expiresAtMillis) { }

    @Override
    public void set(String key, String value, long ttlSeconds) {
        store.put(key, new Entry(value, System.currentTimeMillis() + ttlSeconds * 1000));
    }

    @Override
    public String get(String key) {
        Entry entry = store.get(key);
        if (entry == null) return null;
        if (entry.expiresAtMillis() < System.currentTimeMillis()) {
            store.remove(key);
            return null;
        }
        return entry.value();
    }

    @Override
    public void delete(String key) {
        store.remove(key);
    }

    @Override
    public String consume(String key) {
        Entry entry = store.remove(key);
        return entry == null || entry.expiresAtMillis() <= System.currentTimeMillis() ? null : entry.value();
    }

    @Override
    public long increment(String key, long ttlSeconds) {
        Entry current = store.get(key);
        long next = 1;
        if (current != null) {
            try {
                next = Long.parseLong(current.value()) + 1;
            } catch (NumberFormatException ignored) {
                next = 1;
            }
        }
        set(key, String.valueOf(next), ttlSeconds);
        return next;
    }
}
