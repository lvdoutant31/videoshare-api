package com.yingliu.api.admin.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
public class AdminAuthBypassProperties {
    private final boolean disabled;
    private final long adminId;
    private final boolean localProfile;

    public AdminAuthBypassProperties() {
        this.disabled = false;
        this.adminId = 9000001L;
        this.localProfile = false;
    }

    @Autowired
    public AdminAuthBypassProperties(Environment environment,
            @Value("${yingliu.admin.auth-disabled:false}") boolean disabled,
            @Value("${yingliu.admin.auth-bypass-admin-id:9000001}") long adminId) {
        if (adminId <= 0) {
            throw new IllegalArgumentException("yingliu.admin.auth-bypass-admin-id must be positive");
        }
        this.disabled = disabled;
        this.adminId = adminId;
        this.localProfile = environment.acceptsProfiles(Profiles.of("local"));
    }

    public boolean enabled() {
        return disabled && localProfile;
    }

    public long adminId() {
        return adminId;
    }
}
