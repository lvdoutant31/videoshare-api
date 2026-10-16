package com.yingliu.api.app.security;

import cn.dev33.satoken.stp.StpLogic;
import com.yingliu.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

/** App 域当前用户解析：从独立 StpLogic 取登录用户 ID */
public class AppActorProvider {
    private final StpLogic stpLogic;

    AppActorProvider(StpLogic stpLogic) {
        this.stpLogic = stpLogic;
    }

    /** 已登录用户 ID；未登录抛 AUTH_REQUIRED */
    public long currentUserId() {
        if (!stpLogic.isLogin()) {
            throw new BusinessException("AUTH_REQUIRED", HttpStatus.UNAUTHORIZED, "请先登录");
        }
        try {
            return Long.parseLong(String.valueOf(stpLogic.getLoginId()));
        } catch (NumberFormatException e) {
            throw new BusinessException("AUTH_REQUIRED", HttpStatus.UNAUTHORIZED, "登录身份不合法");
        }
    }

    public boolean loggedIn() {
        return stpLogic.isLogin();
    }

    /** 已登录返回用户 ID，游客返回 null（可选鉴权接口用） */
    public Long optionalUserId() {
        return loggedIn() ? currentUserId() : null;
    }

    public void login(long userId) {
        stpLogic.login(userId);
    }

    public void logout() {
        stpLogic.logout();
    }

    public String currentToken() {
        return stpLogic.getTokenValue();
    }
}
