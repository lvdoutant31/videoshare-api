package com.yingliu.api.admin.security;

import com.yingliu.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class AdminPermissionAuthorizer {
    private final AdminActorProvider actorProvider;
    private final JdbcTemplate jdbcTemplate;

    public AdminPermissionAuthorizer(AdminActorProvider actorProvider, JdbcTemplate jdbcTemplate) {
        this.actorProvider = actorProvider;
        this.jdbcTemplate = jdbcTemplate;
    }

    public long require(String permission) {
        long adminId = actorProvider.currentAdminId();
        String status = jdbcTemplate.query("SELECT status FROM sys_admin_user WHERE id = ?", rs -> rs.next() ? rs.getString(1) : null, adminId);
        if (status == null) throw new BusinessException("ADMIN_NOT_FOUND", HttpStatus.NOT_FOUND, "后台账号不存在");
        if (!"NORMAL".equals(status)) throw new BusinessException("ADMIN_NOT_ELIGIBLE", HttpStatus.UNPROCESSABLE_ENTITY, "后台账号不可用");
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM sys_admin_user_role aur
                JOIN sys_role r ON r.id = aur.role_id AND r.status = 'NORMAL'
                JOIN sys_role_permission rp ON rp.role_id = r.id
                JOIN sys_permission p ON p.id = rp.permission_id
                WHERE aur.admin_user_id = ? AND p.permission_code = ?
                """, Integer.class, adminId, permission);
        if (count == null || count == 0) throw new BusinessException("PERMISSION_DENIED", HttpStatus.FORBIDDEN, "无此操作权限");
        return adminId;
    }
}
