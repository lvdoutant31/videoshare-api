package com.yingliu.api.admin.auth;

import cn.dev33.satoken.stp.StpUtil;
import com.yingliu.common.exception.BusinessException;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AdminAuthService {
    private final JdbcTemplate jdbcTemplate;

    public AdminAuthService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AdminSession login(String username, String password) {
        AdminAccount account = accountByUsername(username);
        if (account == null || !"NORMAL".equals(account.status()) || !BCrypt.checkpw(password, account.passwordHash())) {
            throw new BusinessException("LOGIN_FAILED", HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        StpUtil.login(account.id());
        jdbcTemplate.update("UPDATE sys_admin_user SET last_login_at = ?, updated_at = ? WHERE id = ?", LocalDateTime.now(), LocalDateTime.now(), account.id());
        return session(account.id());
    }

    public AdminSession currentSession() {
        if (!StpUtil.isLogin()) throw new BusinessException("UNAUTHORIZED", HttpStatus.UNAUTHORIZED, "后台登录已失效");
        try {
            return session(Long.parseLong(String.valueOf(StpUtil.getLoginId())));
        } catch (NumberFormatException exception) {
            throw new BusinessException("UNAUTHORIZED", HttpStatus.UNAUTHORIZED, "后台登录身份不合法");
        }
    }

    public void logout() {
        if (StpUtil.isLogin()) StpUtil.logout();
    }

    private AdminSession session(long adminId) {
        AdminAccount account = accountById(adminId);
        if (account == null || !"NORMAL".equals(account.status())) {
            StpUtil.logout();
            throw new BusinessException("UNAUTHORIZED", HttpStatus.UNAUTHORIZED, "后台登录已失效");
        }
        List<String> permissions = jdbcTemplate.query("""
                SELECT DISTINCT p.permission_code
                FROM sys_admin_user_role aur
                JOIN sys_role r ON r.id = aur.role_id AND r.status = 'NORMAL'
                JOIN sys_role_permission rp ON rp.role_id = r.id
                JOIN sys_permission p ON p.id = rp.permission_id
                WHERE aur.admin_user_id = ?
                ORDER BY p.permission_code
                """, (rs, rowNum) -> rs.getString(1), adminId);
        return new AdminSession(account.id(), account.username(), permissions);
    }

    private AdminAccount accountByUsername(String username) {
        return jdbcTemplate.query("SELECT id, username, password_hash, status FROM sys_admin_user WHERE username = ?", rs -> rs.next() ? account(rs) : null, username);
    }

    private AdminAccount accountById(long id) {
        return jdbcTemplate.query("SELECT id, username, password_hash, status FROM sys_admin_user WHERE id = ?", rs -> rs.next() ? account(rs) : null, id);
    }

    private AdminAccount account(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new AdminAccount(rs.getLong("id"), rs.getString("username"), rs.getString("password_hash"), rs.getString("status"));
    }

    private record AdminAccount(long id, String username, String passwordHash, String status) { }
    public record AdminSession(long id, String username, List<String> permissions) { }
}
