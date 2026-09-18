package com.yingliu.api.admin.security;

import cn.dev33.satoken.stp.StpUtil;
import com.yingliu.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class SaTokenAdminActorProvider implements AdminActorProvider {
    private static final Logger log = LoggerFactory.getLogger(SaTokenAdminActorProvider.class);

    private final AdminAuthBypassProperties bypassProperties;

    public SaTokenAdminActorProvider(AdminAuthBypassProperties bypassProperties) {
        this.bypassProperties = bypassProperties;
    }

    @Override
    public long currentAdminId() {
        if (bypassProperties.enabled()) {
            long adminId = bypassProperties.adminId();
            log.info("Admin authorization: stage=identity decision=allow reason=local-bypass adminId={}", adminId);
            return adminId;
        }
        if (!StpUtil.isLogin()) {
            log.info("Admin authorization: stage=identity decision=deny reason=not-logged-in");
            throw new BusinessException("UNAUTHORIZED", HttpStatus.UNAUTHORIZED, "后台登录已失效");
        }
        Object loginId = StpUtil.getLoginId();
        try {
            long adminId = Long.parseLong(String.valueOf(loginId));
            log.info("Admin authorization: stage=identity decision=allow reason=valid-cookie-login-id adminId={}", adminId);
            return adminId;
        } catch (NumberFormatException exception) {
            log.info("Admin authorization: stage=identity decision=deny reason=invalid-login-id loginIdType={}",
                    loginId == null ? "null" : loginId.getClass().getSimpleName());
            throw new BusinessException("UNAUTHORIZED", HttpStatus.UNAUTHORIZED, "后台登录身份不合法");
        }
    }
}
