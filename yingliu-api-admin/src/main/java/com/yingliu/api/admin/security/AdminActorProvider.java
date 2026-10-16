package com.yingliu.api.admin.security;

public interface AdminActorProvider {
    long currentAdminId();

    default void requirePermission(String permission) {
        // Implementations backed only by identity retain compatibility. Production uses AdminPermissionAuthorizer.
    }
}
