package com.yingliu.service.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yingliu.common.exception.BusinessException;
import com.yingliu.domain.content.video.AdminUser;
import com.yingliu.service.content.shared.ServicePage;
import com.yingliu.service.content.video.mapper.AdminUserMapper;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdminAccountService {
    private final AdminUserMapper adminMapper;
    private final JdbcTemplate jdbcTemplate;

    public AdminAccountService(AdminUserMapper adminMapper, JdbcTemplate jdbcTemplate) {
        this.adminMapper = adminMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    public ServicePage<AdminUser> list(long page, long pageSize, String keyword, String status) {
        LambdaQueryWrapper<AdminUser> wrapper = new LambdaQueryWrapper<AdminUser>()
                .like(keyword != null && !keyword.isBlank(), AdminUser::getUsername, keyword)
                .eq(status != null && !status.isBlank(), AdminUser::getStatus, status)
                .orderByDesc(AdminUser::getId);
        Page<AdminUser> result = adminMapper.selectPage(new Page<>(page, pageSize), wrapper);
        return new ServicePage<>(result.getRecords(), result.getTotal());
    }

    public AdminUser get(long adminId) {
        return requireAdmin(adminId);
    }

    public List<String> getRoleCodes(long adminId) {
        return adminMapper.selectRoleCodes(adminId);
    }

    public List<AdminUserMapper.RoleView> getRoles(long adminId) {
        return adminMapper.selectRoles(adminId);
    }

    public List<AdminUserMapper.RoleView> listAllActiveRoles() {
        return adminMapper.selectAllActiveRoles();
    }

    public List<PermissionView> permissionCatalog() {
        return jdbcTemplate.query("SELECT permission_code, permission_name FROM sys_permission "
                        + "WHERE permission_code LIKE '%:view' AND permission_code NOT IN ('storage:view', 'report:view') "
                        + "ORDER BY sort, permission_code",
                (rs, rowNum) -> new PermissionView(rs.getString(1), rs.getString(2)));
    }

    public List<String> getPermissionCodes(long adminId) {
        requireAdmin(adminId);
        List<String> granted = jdbcTemplate.query("SELECT p.permission_code FROM sys_admin_user_permission aup "
                        + "JOIN sys_permission p ON p.id = aup.permission_id "
                        + "WHERE aup.admin_user_id = ? ORDER BY p.sort, p.permission_code",
                (rs, rowNum) -> rs.getString(1), adminId);
        java.util.Set<String> menuPrefixes = new java.util.HashSet<>();
        for (String code : granted) {
            int separator = code.indexOf(':');
            if (separator < 0) continue;
            String menu = code.substring(0, separator);
            if ("storage".equals(menu)) menu = "dashboard";
            if ("report".equals(menu)) menu = "comment";
            menuPrefixes.add(menu);
        }
        return permissionCatalog().stream().map(PermissionView::code).filter(code -> {
            String menu = code.substring(0, code.indexOf(':'));
            return menuPrefixes.contains(menu);
        }).toList();
    }

    @Transactional
    public void updatePermissions(long adminId, List<String> permissionCodes) {
        requireAdmin(adminId);
        if (isSuperAdmin(adminId)) {
            throw new BusinessException("STATE_CONFLICT", HttpStatus.CONFLICT, "不能修改超级管理员的授权");
        }
        List<String> supported = permissionCatalog().stream().map(PermissionView::code).toList();
        List<String> requested = permissionCodes == null ? List.of() : permissionCodes.stream().distinct().toList();
        if (!supported.containsAll(requested)) {
            throw new BusinessException("VALIDATION_FAILED", HttpStatus.UNPROCESSABLE_ENTITY, "权限列表包含无效权限");
        }
        java.util.Set<String> expanded = new java.util.LinkedHashSet<>(requested);
        for (String menuCode : requested) {
            String prefix = menuCode.substring(0, menuCode.indexOf(':'));
            if ("dashboard".equals(prefix)) expanded.add("storage:view");
            if ("comment".equals(prefix)) expanded.add("report:view");
            String actionPrefix = "comment".equals(prefix) ? "(comment|report)" : prefix;
            expanded.addAll(jdbcTemplate.query("SELECT permission_code FROM sys_permission WHERE permission_code REGEXP ? "
                            + "AND permission_code <> 'admin:permissions'",
                    (rs, rowNum) -> rs.getString(1), "^" + actionPrefix + ":"));
        }
        expanded.remove("admin:permissions");
        jdbcTemplate.update("DELETE FROM sys_admin_user_permission WHERE admin_user_id = ?", adminId);
        for (String code : expanded) {
            jdbcTemplate.update("INSERT INTO sys_admin_user_permission (admin_user_id, permission_id, created_at) "
                            + "SELECT ?, id, NOW(3) FROM sys_permission WHERE permission_code = ?",
                    adminId, code);
        }
    }

    private boolean isSuperAdmin(long adminId) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_admin_user_role aur "
                        + "JOIN sys_role r ON r.id = aur.role_id WHERE aur.admin_user_id = ? "
                        + "AND r.role_code = 'SUPER_ADMIN' AND r.status = 'NORMAL'",
                Integer.class, adminId);
        return count != null && count > 0;
    }

    @Transactional
    public AdminUser create(String username, String password) {
        validateUsername(username);
        validatePassword(password);
        AdminUser admin = new AdminUser();
        admin.setUsername(username.trim());
        admin.setPasswordHash(BCrypt.hashpw(password, BCrypt.gensalt()));
        admin.setStatus("NORMAL");
        adminMapper.insert(admin);
        return admin;
    }

    @Transactional
    public void resetPassword(long adminId, String newPassword) {
        requireAdmin(adminId);
        validatePassword(newPassword);
        AdminUser update = new AdminUser();
        update.setId(adminId);
        update.setPasswordHash(BCrypt.hashpw(newPassword, BCrypt.gensalt()));
        adminMapper.updateById(update);
    }

    @Transactional
    public AdminUser updateStatus(long adminId, String status) {
        AdminUser admin = requireAdmin(adminId);
        if (!"NORMAL".equals(status) && !"DISABLED".equals(status)) {
            throw new BusinessException("VALIDATION_FAILED", HttpStatus.UNPROCESSABLE_ENTITY, "状态必须为 NORMAL 或 DISABLED");
        }
        if ("DISABLED".equals(status) && "NORMAL".equals(admin.getStatus()) && isSuperAdmin(adminId)) {
            List<Long> activeSuperAdmins = jdbcTemplate.query("SELECT u.id FROM sys_admin_user u "
                            + "JOIN sys_admin_user_role aur ON aur.admin_user_id = u.id "
                            + "JOIN sys_role r ON r.id = aur.role_id AND r.role_code = 'SUPER_ADMIN' "
                            + "WHERE u.status = 'NORMAL' ORDER BY u.id FOR UPDATE",
                    (rs, rowNum) -> rs.getLong(1));
            if (activeSuperAdmins.size() <= 1) {
                throw new BusinessException("STATE_CONFLICT", HttpStatus.CONFLICT, "不能停用最后一个正常状态的超级管理员");
            }
        }
        admin.setStatus(status);
        adminMapper.updateById(admin);
        return admin;
    }

    private AdminUser requireAdmin(long adminId) {
        AdminUser admin = adminMapper.selectById(adminId);
        if (admin == null) {
            throw new BusinessException("ADMIN_NOT_FOUND", HttpStatus.NOT_FOUND, "管理员不存在");
        }
        return admin;
    }

    private void validateUsername(String username) {
        String normalized = username == null ? "" : username.trim();
        if (normalized.isEmpty() || normalized.length() > 64) {
            throw new BusinessException("VALIDATION_FAILED", HttpStatus.UNPROCESSABLE_ENTITY, "用户名需为 1 至 64 个字符");
        }
        // Check uniqueness
        Long count = adminMapper.selectCount(new LambdaQueryWrapper<AdminUser>().eq(AdminUser::getUsername, normalized));
        if (count != null && count > 0) {
            throw new BusinessException("USERNAME_DUPLICATE", HttpStatus.CONFLICT, "用户名已存在");
        }
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 6 || password.length() > 128) {
            throw new BusinessException("VALIDATION_FAILED", HttpStatus.UNPROCESSABLE_ENTITY, "密码长度需为 6 至 128 个字符");
        }
    }

    public record PermissionView(String code, String name) { }
}
