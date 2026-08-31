package com.yingliu.api.admin.security;

import cn.dev33.satoken.stp.StpUtil;
import com.yingliu.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class SaTokenAdminActorProvider implements AdminActorProvider {
    @Override
    public long currentAdminId() {
        if (!StpUtil.isLogin()) {
            throw new BusinessException("UNAUTHORIZED", HttpStatus.UNAUTHORIZED, "后台登录已失效");
        }
        Object loginId = StpUtil.getLoginId();
        try {
            return Long.parseLong(String.valueOf(loginId));
        } catch (NumberFormatException exception) {
            throw new BusinessException("UNAUTHORIZED", HttpStatus.UNAUTHORIZED, "后台登录身份不合法");
        }
    }
}
