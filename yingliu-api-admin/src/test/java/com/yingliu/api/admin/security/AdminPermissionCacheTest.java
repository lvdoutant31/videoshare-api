package com.yingliu.api.admin.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 授权缓存的行为契约：命中、版本失效与损坏自愈。 */
class AdminPermissionCacheTest {

    @Test
    void reusesCachedGrantsUntilVersionChanges() {
        CountingJdbcTemplate jdbc = new CountingJdbcTemplate(List.of("video:view"), List.of("ADMIN"));
        AdminPermissionCache cache = new AdminPermissionCache(jdbc, new FakeTtlStore(), new ObjectMapper());

        assertEquals(List.of("video:view"), cache.grants(7L).permissions());
        assertEquals(List.of("ADMIN"), cache.grants(7L).roleCodes());
        assertEquals(1, jdbc.permissionQueryCount, "第二次读取应命中缓存");

        cache.invalidateAll();

        cache.grants(7L);
        assertEquals(2, jdbc.permissionQueryCount, "版本号变更后必须回源数据库");
    }

    @Test
    void initializesVersionWithTimestampSoExpiredVersionCannotReplayOldEntries() {
        FakeTtlStore store = new FakeTtlStore();
        AdminPermissionCache cache = new AdminPermissionCache(
                new CountingJdbcTemplate(List.of("video:view"), List.of("ADMIN")), store, new ObjectMapper());

        cache.grants(7L);

        String version = store.get("yingliu:auth:perm:ver");
        assertNotNull(version);
        assertTrue(Long.parseLong(version) > 1_600_000_000_000L,
                "版本号需以时间戳初始化：版本键过期重建时不能复现历史版本号，否则会命中陈旧权限");
    }

    @Test
    void recoversFromUnreadableCacheEntry() {
        FakeTtlStore store = new FakeTtlStore();
        store.putRaw("yingliu:auth:perm:ver", "5");
        store.putRaw("yingliu:auth:perm:v5:admin:7", "{not-json");
        CountingJdbcTemplate jdbc = new CountingJdbcTemplate(List.of("video:view"), List.of("ADMIN"));

        List<String> permissions = new AdminPermissionCache(jdbc, store, new ObjectMapper()).grants(7L).permissions();

        assertEquals(List.of("video:view"), permissions);
        assertEquals(1, jdbc.permissionQueryCount, "缓存不可解析时应回源数据库，而不是让鉴权失败");
    }

    private static final class CountingJdbcTemplate extends JdbcTemplate {
        private final List<String> permissions;
        private final List<String> roles;
        private int permissionQueryCount;

        private CountingJdbcTemplate(List<String> permissions, List<String> roles) {
            this.permissions = permissions;
            this.roles = roles;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            assertEquals(7L, args[0]);
            if (sql.contains("sys_permission")) {
                permissionQueryCount++;
                return (List<T>) permissions;
            }
            if (sql.contains("sys_role")) {
                return (List<T>) roles;
            }
            throw new AssertionError("Unexpected SQL: " + sql);
        }
    }
}
