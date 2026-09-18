package com.yingliu.api.admin.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yingliu.service.support.TtlStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 管理端授权关系缓存：把"账号 → 权限码 / 角色码"的多次表连接结果缓存到 Redis。
 *
 * <p>鉴权在每个受保护请求上都会发生，而权限来自四张表的连接查询；缓存后命中路径不再访问数据库。
 *
 * <p><b>失效策略</b>：缓存键内嵌全局版本号，任何授权关系变更只需让版本号自增，
 * 全部旧键即逻辑失效，无需 {@code KEYS}/{@code SCAN}。版本号缺失时用当前时间戳初始化，
 * 因此即使版本号键因过期被重建，也不会复现历史版本号而命中陈旧数据。
 *
 * <p>角色定义与"角色-权限"映射目前只由 Flyway 迁移维护，因此运维直接改库后
 * 必须手动触发一次失效，否则最长 {@value #GRANTS_TTL_SECONDS} 秒内仍使用旧权限。
 */
@Component
public class AdminPermissionCache {
    private static final Logger log = LoggerFactory.getLogger(AdminPermissionCache.class);

    private static final String VERSION_KEY = "yingliu:auth:perm:ver";
    private static final String GRANTS_KEY = "yingliu:auth:perm:v%s:admin:%d";

    /** 授权缓存有效期；必须远小于版本号有效期，避免版本号过期重建后命中旧数据。 */
    private static final long GRANTS_TTL_SECONDS = 600;

    /** 版本号有效期 90 天：长期无授权变更时被回收也不会造成陈旧命中。 */
    private static final long VERSION_TTL_SECONDS = 7_776_000L;

    private static final String PERMISSIONS_SQL = """
            SELECT DISTINCT p.permission_code
            FROM sys_admin_user_role aur
            JOIN sys_role r ON r.id = aur.role_id AND r.status = 'NORMAL'
            JOIN sys_role_permission rp ON rp.role_id = r.id
            JOIN sys_permission p ON p.id = rp.permission_id
            WHERE aur.admin_user_id = ?
            ORDER BY p.permission_code
            """;

    private static final String ROLES_SQL = """
            SELECT DISTINCT r.role_code
            FROM sys_admin_user_role aur
            JOIN sys_role r ON r.id = aur.role_id AND r.status = 'NORMAL'
            WHERE aur.admin_user_id = ?
            ORDER BY r.role_code
            """;

    private final JdbcTemplate jdbcTemplate;
    private final TtlStore store;
    private final ObjectMapper objectMapper;

    public AdminPermissionCache(JdbcTemplate jdbcTemplate, TtlStore store, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.store = store;
        this.objectMapper = objectMapper;
    }

    /** 账号当前生效的权限码与角色码；命中缓存时零次数据库访问。 */
    public Grants grants(long adminId) {
        long version = version();
        String key = GRANTS_KEY.formatted(version, adminId);
        String cached = store.get(key);
        if (cached != null) {
            Grants parsed = parse(cached);
            if (parsed != null) {
                log.info("Admin authorization: stage=permission-cache result=hit adminId={} version={} roles={} permissions={}",
                        adminId, version, parsed.roleCodes(), parsed.permissions());
                return parsed;
            }
            log.info("Admin authorization: stage=permission-cache result=invalid adminId={} version={}",
                    adminId, version);
        } else {
            log.info("Admin authorization: stage=permission-cache result=miss adminId={} version={}",
                    adminId, version);
        }
        Grants fresh = new Grants(
                jdbcTemplate.query(PERMISSIONS_SQL, (rs, rowNum) -> rs.getString(1), adminId),
                jdbcTemplate.query(ROLES_SQL, (rs, rowNum) -> rs.getString(1), adminId));
        log.info("Admin authorization: stage=permission-database result=loaded adminId={} roles={} permissions={}",
                adminId, fresh.roleCodes(), fresh.permissions());
        store.set(key, serialize(fresh), GRANTS_TTL_SECONDS);
        log.info("Admin authorization: stage=permission-cache result=stored adminId={} version={} ttlSeconds={}",
                adminId, version, GRANTS_TTL_SECONDS);
        return fresh;
    }

    /**
     * 让全部授权缓存失效。账号角色变更、角色或权限定义变更、管理员启停后都应调用，
     * 使新的授权关系在下一个请求即生效。
     */
    public void invalidateAll() {
        store.increment(VERSION_KEY, VERSION_TTL_SECONDS);
        log.info("Admin authorization: stage=permission-cache result=invalidated scope=all");
    }

    private long version() {
        String current = store.get(VERSION_KEY);
        if (current != null) {
            try {
                long version = Long.parseLong(current);
                log.info("Admin authorization: stage=permission-cache-version result=hit version={}", version);
                return version;
            } catch (NumberFormatException ignored) {
                log.info("Admin authorization: stage=permission-cache-version result=invalid valueType=non-numeric");
                // 值不可解析时按缺失处理，重新初始化。
            }
        }
        long initial = System.currentTimeMillis();
        store.set(VERSION_KEY, String.valueOf(initial), VERSION_TTL_SECONDS);
        log.info("Admin authorization: stage=permission-cache-version result=initialized proposedVersion={}", initial);
        String settled = store.get(VERSION_KEY);
        if (settled == null) {
            log.info("Admin authorization: stage=permission-cache-version result=use-proposed reason=missing-after-initialize version={}",
                    initial);
            return initial;
        }
        try {
            long version = Long.parseLong(settled);
            log.info("Admin authorization: stage=permission-cache-version result=settled version={}", version);
            return version;
        } catch (NumberFormatException ignored) {
            log.info("Admin authorization: stage=permission-cache-version result=use-proposed reason=invalid-after-initialize version={}",
                    initial);
            // 并发初始化时另一请求写入了非数字值；以本次时间戳为准，最坏结果只是一次缓存未命中。
            return initial;
        }
    }

    private String serialize(Grants grants) {
        try {
            return objectMapper.writeValueAsString(grants);
        } catch (JsonProcessingException exception) {
            log.info("Admin authorization: stage=permission-cache-serialize result=failed reason={}",
                    exception.getClass().getSimpleName());
            // 序列化失败不应影响鉴权：写入空串让下一次读回按未命中处理。
            return "";
        }
    }

    private Grants parse(String raw) {
        if (raw.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(raw, Grants.class);
        } catch (JsonProcessingException exception) {
            log.info("Admin authorization: stage=permission-cache-parse result=failed reason={}",
                    exception.getClass().getSimpleName());
            // 缓存格式变更或数据损坏时按未命中处理，回源数据库即可自愈。
            return null;
        }
    }

    /** 账号的生效授权：权限码用于接口校验，角色码用于会话展示。 */
    public record Grants(List<String> permissions, List<String> roleCodes) {
    }

}
