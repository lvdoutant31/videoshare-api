package com.yingliu.api.admin.auth;

import cn.dev33.satoken.stp.StpUtil;
import com.yingliu.api.admin.security.AdminAuthBypassProperties;
import com.yingliu.api.admin.security.AdminAuthorizationProperties;
import com.yingliu.api.admin.security.AdminLoginGuard;
import com.yingliu.api.admin.security.AdminPermissionCache;
import com.yingliu.common.exception.BusinessException;
import org.mindrot.jbcrypt.BCrypt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/** 管理端登录用例，协调风控、账号查询、密码校验和 Sa-Token 会话。 */
@Service
public class AdminAuthService {
    private static final Logger log = LoggerFactory.getLogger(AdminAuthService.class);

    private final JdbcTemplate jdbcTemplate;
    private final AdminAuthBypassProperties bypassProperties;
    private final AdminAuthorizationProperties authorizationProperties;
    private final AdminLoginGuard loginGuard;
    private final AdminPermissionCache permissionCache;

    public AdminAuthService(JdbcTemplate jdbcTemplate, AdminAuthBypassProperties bypassProperties,
                            AdminAuthorizationProperties authorizationProperties,
                            AdminLoginGuard loginGuard, AdminPermissionCache permissionCache) {
        this.jdbcTemplate = jdbcTemplate;
        this.bypassProperties = bypassProperties;
        this.authorizationProperties = authorizationProperties;
        this.loginGuard = loginGuard;
        this.permissionCache = permissionCache;
    }

    /**
     * 账号密码登录。
     *
     * <p>先经风控检查（锁定 / IP 限流），再校验密码；失败累计次数，成功清零。
     * 账号不存在与密码错误返回同一提示，避免通过响应差异枚举账号。
     *
     * @param clientIp 登录来源地址，仅用于可选的 IP 限流
     */
    public AdminSession login(String username, String password, String clientIp) {
        loginGuard.beforeAttempt(username, clientIp);
        AdminAccount account = accountByUsername(username);
        if (account == null || !"NORMAL".equals(account.status()) || !BCrypt.checkpw(password, account.passwordHash())) {
            loginGuard.onFailure(username);
            throw new BusinessException("LOGIN_FAILED", HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        loginGuard.onSuccess(username);
        if (authorizationProperties.enabled()) {
            StpUtil.login(account.id());
        }
        jdbcTemplate.update("UPDATE sys_admin_user SET last_login_at = ?, updated_at = ? WHERE id = ?", LocalDateTime.now(), LocalDateTime.now(), account.id());
        AdminSession result = session(account.id());
        log.info("管理员登录成功 adminId={} username={} roleCount={} permissionCount={}",
                result.id(), result.username(), result.roles().size(), result.permissions().size());
        return result;
    }

    public AdminSession currentSession() {
        if (!authorizationProperties.enabled()) return session(bypassProperties.adminId());
        if (bypassProperties.enabled()) return session(bypassProperties.adminId());
        if (!StpUtil.isLogin()) throw new BusinessException("UNAUTHORIZED", HttpStatus.UNAUTHORIZED, "后台登录已失效");
        try {
            return session(Long.parseLong(String.valueOf(StpUtil.getLoginId())));
        } catch (NumberFormatException exception) {
            throw new BusinessException("UNAUTHORIZED", HttpStatus.UNAUTHORIZED, "后台登录身份不合法");
        }
    }

    public void logout() {
        if (authorizationProperties.enabled() && !bypassProperties.enabled() && StpUtil.isLogin()) StpUtil.logout();
    }

    private AdminSession session(long adminId) {
        AdminAccount account = accountById(adminId);
        if (account == null || !"NORMAL".equals(account.status())) {
            StpUtil.logout();
            throw new BusinessException("UNAUTHORIZED", HttpStatus.UNAUTHORIZED, "后台登录已失效");
        }
        AdminPermissionCache.Grants grants = permissionCache.grants(adminId);
        return new AdminSession(account.id(), account.username(), grants.permissions(), grants.roleCodes(), expiresInSeconds());
    }

    /**
     * 当前会话剩余有效期（秒），供前端做临期提示与到期跳转；
     * 本地调试旁路没有真实会话，返回 null 表示不适用。
     */
    private Long expiresInSeconds() {
        if (!authorizationProperties.enabled() || bypassProperties.enabled()) return null;
        long remaining = StpUtil.getTokenTimeout();
        return remaining > 0 ? remaining : null;
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

    /** 登录会话视图：权限码用于接口与菜单裁决，角色码用于展示，expiresIn 用于前端倒计时。 */
    public record AdminSession(long id, String username, List<String> permissions, List<String> roles, Long expiresIn) { }
}
