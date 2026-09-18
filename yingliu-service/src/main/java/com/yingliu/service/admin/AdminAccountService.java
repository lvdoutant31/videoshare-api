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

import java.time.LocalDateTime;
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
    public AdminUser updateRoles(long adminId, List<String> roleCodes) {
        AdminUser admin = requireAdmin(adminId);
        syncRoles(adminId, roleCodes);
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

    private void syncRoles(long adminId, List<String> roleCodes) {
        // Delete existing role assignments
        jdbcTemplate.update("DELETE FROM sys_admin_user_role WHERE admin_user_id = ?", adminId);
        // Insert new role assignments
        if (roleCodes != null && !roleCodes.isEmpty()) {
            LocalDateTime now = LocalDateTime.now();
            for (String roleCode : roleCodes) {
                jdbcTemplate.update(
                        "INSERT INTO sys_admin_user_role (admin_user_id, role_id, created_at) " +
                                "SELECT ?, r.id, ? FROM sys_role r WHERE r.role_code = ? AND r.status = 'NORMAL'",
                        adminId, now, roleCode);
            }
        }
    }
}
