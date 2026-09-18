package com.yingliu.api.admin.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class AdminAccountDtos {
    private AdminAccountDtos() { }

    public record Create(@NotBlank @Size(max = 64) String username,
                         @NotBlank @Size(min = 6, max = 128) String password) { }

    public record UpdateRoles(@NotEmpty List<@NotBlank String> roleCodes) { }

    public record ResetPassword(@NotBlank @Size(min = 6, max = 128) String newPassword) { }

    public record UpdateStatus(@NotBlank String status) { }

    public record RoleItem(String code, String name) { }

    public record Summary(String id, String username, String status,
                          List<String> roleCodes,
                          String lastLoginAt, String createdAt) { }

    public record View(String id, String username, String status,
                       List<RoleItem> roles,
                       String lastLoginAt, String createdAt) { }
}
