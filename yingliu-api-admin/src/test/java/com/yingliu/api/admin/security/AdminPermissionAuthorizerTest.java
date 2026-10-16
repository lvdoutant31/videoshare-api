package com.yingliu.api.admin.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yingliu.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 管理端权限校验的行为契约。
 *
 * <p>重点验证两点设计取舍：权限码走缓存（重复调用不再查库），
 * 账号状态逐次实时查询（停用必须立即生效）。
 */
class AdminPermissionAuthorizerTest {
    private static final String STATUS_SQL = "SELECT status FROM sys_admin_user WHERE id = ?";

    @Test
    void allowsNormalAdminWithGrantedPermission() {
        GrantsJdbcTemplate jdbc = new GrantsJdbcTemplate("NORMAL", List.of("user:manage"), List.of("ADMIN"));

        long adminId = authorizer(jdbc).require("user:manage");

        assertEquals(7L, adminId);
        assertEquals(1, jdbc.statusQueryCount);
        assertEquals(1, jdbc.permissionQueryCount);
    }

    @Test
    void reusesCachedGrantsButRebuildsAccountStatus() {
        GrantsJdbcTemplate jdbc = new GrantsJdbcTemplate("NORMAL", List.of("user:manage"), List.of("ADMIN"));
        AdminPermissionAuthorizer authorizer = authorizer(jdbc);

        authorizer.require("user:manage");
        authorizer.require("user:manage");

        assertEquals(2, jdbc.statusQueryCount, "账号状态必须逐次实时查询，否则停用会延迟生效");
        assertEquals(1, jdbc.permissionQueryCount, "权限码应命中缓存，不重复执行多表连接");
    }

    @Test
    void reloadsGrantsAfterCacheInvalidation() {
        GrantsJdbcTemplate jdbc = new GrantsJdbcTemplate("NORMAL", List.of("user:manage"), List.of("ADMIN"));
        FakeTtlStore store = new FakeTtlStore();
        AdminPermissionCache cache = new AdminPermissionCache(jdbc, store, new ObjectMapper());
        AdminPermissionAuthorizer authorizer = new AdminPermissionAuthorizer(() -> 7L, jdbc, cache);

        authorizer.require("user:manage");
        cache.invalidateAll();
        authorizer.require("user:manage");

        assertEquals(2, jdbc.permissionQueryCount, "授权变更后必须重新回源");
    }

    @Test
    void rejectsNormalAdminWithoutPermission() {
        GrantsJdbcTemplate jdbc = new GrantsJdbcTemplate("NORMAL", List.of("video:view"), List.of("EDITOR"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authorizer(jdbc).require("admin:create"));

        assertEquals("PERMISSION_DENIED", exception.getCode());
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
    }

    @Test
    void stillRejectsUnavailableAdmin() {
        GrantsJdbcTemplate jdbc = new GrantsJdbcTemplate("DISABLED", List.of("user:manage"), List.of("ADMIN"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authorizer(jdbc).require("user:manage"));

        assertEquals("ADMIN_NOT_ELIGIBLE", exception.getCode());
    }

    @Test
    void stillRejectsMissingAdmin() {
        GrantsJdbcTemplate jdbc = new GrantsJdbcTemplate(null, List.of("user:manage"), List.of("ADMIN"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authorizer(jdbc).require("user:manage"));

        assertEquals("ADMIN_NOT_FOUND", exception.getCode());
    }

    @Test
    void stillRequiresAuthenticatedAdminIdentity() {
        GrantsJdbcTemplate jdbc = new GrantsJdbcTemplate("NORMAL", List.of("user:manage"), List.of("ADMIN"));
        AdminPermissionAuthorizer authorizer = new AdminPermissionAuthorizer(() -> {
            throw new BusinessException("UNAUTHORIZED", HttpStatus.UNAUTHORIZED, "后台登录已失效");
        }, jdbc, cacheFor(jdbc));

        BusinessException exception = assertThrows(BusinessException.class, () -> authorizer.require("user:manage"));

        assertEquals("UNAUTHORIZED", exception.getCode());
        assertEquals(0, jdbc.statusQueryCount);
        assertEquals(0, jdbc.permissionQueryCount);
    }

    @Test
    void skipsPermissionAndAccountChecksWhenAuthorizationIsDisabled() {
        GrantsJdbcTemplate jdbc = new GrantsJdbcTemplate("DISABLED", List.of(), List.of());
        AdminPermissionAuthorizer authorizer = new AdminPermissionAuthorizer(() -> {
            throw new AssertionError("关闭授权后不应读取 Cookie 登录身份");
        }, jdbc,
                cacheFor(jdbc), new AdminAuthBypassProperties(), new AdminAuthorizationProperties(false));

        long adminId = authorizer.require("admin:create");

        assertEquals(9000001L, adminId);
        assertEquals(0, jdbc.statusQueryCount);
        assertEquals(0, jdbc.permissionQueryCount);
    }

    private AdminPermissionAuthorizer authorizer(JdbcTemplate jdbc) {
        return new AdminPermissionAuthorizer(() -> 7L, jdbc, cacheFor(jdbc));
    }

    private AdminPermissionCache cacheFor(JdbcTemplate jdbc) {
        return new AdminPermissionCache(jdbc, new FakeTtlStore(), new ObjectMapper());
    }

    /** 只实现鉴权路径用到的两个查询重载，其余访问即视为意外调用。 */
    private static final class GrantsJdbcTemplate extends JdbcTemplate {
        private final String status;
        private final List<String> permissions;
        private final List<String> roles;
        private int statusQueryCount;
        private int permissionQueryCount;

        private GrantsJdbcTemplate(String status, List<String> permissions, List<String> roles) {
            this.status = status;
            this.permissions = permissions;
            this.roles = roles;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T query(String sql, ResultSetExtractor<T> resultSetExtractor, Object... args) {
            assertEquals(STATUS_SQL, sql);
            assertEquals(7L, args[0]);
            statusQueryCount++;
            return (T) status;
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
