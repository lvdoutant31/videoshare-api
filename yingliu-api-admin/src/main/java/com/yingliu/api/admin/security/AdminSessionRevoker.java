package com.yingliu.api.admin.security;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.stereotype.Component;

/**
 * 管理端会话与授权缓存的失效入口。
 *
 * <p>授权或凭据变化后必须由此收口，否则会出现两类问题：
 * 改密、停用、删号后旧会话仍能继续访问；角色变更后仍在一段时间内沿用旧权限。
 */
@Component
public class AdminSessionRevoker {
    private final AdminPermissionCache permissionCache;

    public AdminSessionRevoker(AdminPermissionCache permissionCache) {
        this.permissionCache = permissionCache;
    }

    /**
     * 立即断开该账号的全部会话并刷新授权缓存。
     * 用于改密、停用、删除账号：会话失效后用户下次请求收到 401，前端跳转登录页重新认证。
     */
    public void revoke(long adminId) {
        StpUtil.logout(adminId);
        permissionCache.invalidateAll();
    }

    /** 仅刷新授权缓存，不打断在线会话；用于新增或调整授权，使新权限在下一个请求即生效。 */
    public void refreshGrants() {
        permissionCache.invalidateAll();
    }

    /**
     * 强制下线：会话被标记为"被顶下线"，用户下次请求收到"账号已在其他设备登录"，
     * 与 {@link #revoke(long)} 的"登录已过期"提示相区分。
     */
    public void kickout(long adminId) {
        StpUtil.kickout(adminId);
        permissionCache.invalidateAll();
    }
}
