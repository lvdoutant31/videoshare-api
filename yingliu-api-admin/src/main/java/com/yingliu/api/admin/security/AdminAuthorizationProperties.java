package com.yingliu.api.admin.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 控制登录后的具体权限码校验；关闭时仍要求先通过 Cookie 登录。 */
@Component
public class AdminAuthorizationProperties {
    private final boolean enabled;

    public AdminAuthorizationProperties() {
        this(true);
    }

    @Autowired
    public AdminAuthorizationProperties(
            @Value("${yingliu.admin.authorization-enabled:true}") boolean enabled) {
        this.enabled = enabled;
    }

    public boolean enabled() {
        return enabled;
    }
}
