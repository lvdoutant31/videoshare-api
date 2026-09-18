package com.yingliu.api.admin.security;

import com.yingliu.service.support.TtlStore;

import java.util.HashMap;
import java.util.Map;

/**
 * 供单元测试使用的 {@link TtlStore} 假实现：带过期判断的内存 Map，
 * 使计数、锁定与缓存逻辑无需 Redis 即可验证。
 */
public final class FakeTtlStore implements TtlStore {
    private final Map<String, String> values = new HashMap<>();
    private final Map<String, Long> expiries = new HashMap<>();

    @Override
    public void set(String key, String value, long ttlSeconds) {
        values.put(key, value);
        expiries.put(key, System.currentTimeMillis() + ttlSeconds * 1000);
    }

    @Override
    public String get(String key) {
        if (!values.containsKey(key)) {
            return null;
        }
        if (expiries.getOrDefault(key, Long.MAX_VALUE) < System.currentTimeMillis()) {
            delete(key);
            return null;
        }
        return values.get(key);
    }

    @Override
    public void delete(String key) {
        values.remove(key);
        expiries.remove(key);
    }

    @Override
    public String consume(String key) {
        String value = get(key);
        delete(key);
        return value;
    }

    @Override
    public long increment(String key, long ttlSeconds) {
        long next = 1;
        String current = get(key);
        if (current != null) {
            try {
                next = Long.parseLong(current) + 1;
            } catch (NumberFormatException ignored) {
                next = 1;
            }
        }
        set(key, String.valueOf(next), ttlSeconds);
        return next;
    }

    public void putRaw(String key, String value) {
        set(key, value, 3600);
    }

    boolean contains(String key) {
        return get(key) != null;
    }
}
