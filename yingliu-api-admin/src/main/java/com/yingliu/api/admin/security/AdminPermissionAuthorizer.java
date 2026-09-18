package com.yingliu.api.admin.security;

import com.yingliu.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 管理端接口权限校验入口，供各控制器在方法内调用。
 *
 * <p>权限码来自 {@link AdminPermissionCache}（命中缓存时零次数据库访问）。
 * 账号状态仍逐次实时查询：单个主键查询开销极低，而缓存会让停用操作产生生效延迟，
 * 这是安全上不能接受的取舍。
 */
@Component
public class AdminPermissionAuthorizer {
    private static final Logger log = LoggerFactory.getLogger(AdminPermissionAuthorizer.class);

    private final AdminActorProvider actorProvider;
    private final JdbcTemplate jdbcTemplate;
    private final AdminPermissionCache permissionCache;
    private final AdminAuthBypassProperties bypassProperties;
    private final AdminAuthorizationProperties authorizationProperties;

    public AdminPermissionAuthorizer(AdminActorProvider actorProvider, JdbcTemplate jdbcTemplate,
                                     AdminPermissionCache permissionCache) {
        this(actorProvider, jdbcTemplate, permissionCache, new AdminAuthBypassProperties(),
                new AdminAuthorizationProperties());
    }

    @Autowired
    public AdminPermissionAuthorizer(AdminActorProvider actorProvider, JdbcTemplate jdbcTemplate,
                                     AdminPermissionCache permissionCache,
                                     AdminAuthBypassProperties bypassProperties,
                                     AdminAuthorizationProperties authorizationProperties) {
        this.actorProvider = actorProvider;
        this.jdbcTemplate = jdbcTemplate;
        this.permissionCache = permissionCache;
        this.bypassProperties = bypassProperties;
        this.authorizationProperties = authorizationProperties;
    }

    /**
     * 校验当前管理员是否具备指定权限，返回其账号 ID 供审计与业务记录使用。
     *
     * @throws BusinessException 账号不存在或不可用时按对应状态码拒绝；缺少权限时返回 403。
     */
    public long require(String permission) {
        log.info("Admin authorization: stage=permission decision=evaluate permission={}", permission);
        if (!authorizationProperties.enabled()) {
            long adminId = bypassProperties.adminId();
            log.info("Admin authorization: stage=permission decision=allow reason=authorization-disabled adminId={} permission={}",
                    adminId, permission);
            return adminId;
        }
        if (bypassProperties.enabled()) {
            long adminId = bypassProperties.adminId();
            log.info("Admin authorization: stage=permission decision=allow reason=local-bypass adminId={} permission={}",
                    adminId, permission);
            return adminId;
        }
        long adminId = actorProvider.currentAdminId();
        requireActive(adminId);
        AdminPermissionCache.Grants grants = permissionCache.grants(adminId);
        if (!grants.permissions().contains(permission)) {
            log.info("Admin authorization: stage=permission decision=deny reason=permission-missing adminId={} permission={} roles={} permissions={}",
                    adminId, permission, grants.roleCodes(), grants.permissions());
            throw new BusinessException("PERMISSION_DENIED", HttpStatus.FORBIDDEN, "无权执行此操作");
        }
        log.info("Admin authorization: stage=permission decision=allow reason=permission-granted adminId={} permission={} roles={}",
                adminId, permission, grants.roleCodes());
        return adminId;
    }

    private void requireActive(long adminId) {
        String status = jdbcTemplate.query("SELECT status FROM sys_admin_user WHERE id = ?",
                rs -> rs.next() ? rs.getString(1) : null, adminId);
        if (status == null) {
            log.info("Admin authorization: stage=account-status decision=deny reason=admin-not-found adminId={}", adminId);
            throw new BusinessException("ADMIN_NOT_FOUND", HttpStatus.NOT_FOUND, "后台账号不存在");
        }
        if (!"NORMAL".equals(status)) {
            log.info("Admin authorization: stage=account-status decision=deny reason=admin-not-eligible adminId={} status={}",
                    adminId, status);
            throw new BusinessException("ADMIN_NOT_ELIGIBLE", HttpStatus.UNPROCESSABLE_ENTITY, "后台账号不可用");
        }
        log.info("Admin authorization: stage=account-status decision=allow adminId={} status={}", adminId, status);
    }
}
