package com.yingliu.api.admin.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class AdminAccountDtos {
    private AdminAccountDtos() { }

    public record Create(@NotBlank @Size(max = 64) String username,
                         @NotBlank @Size(min = 6, max = 128) String password) { }

    public record UpdatePermissions(@NotNull List<@NotBlank String> permissionCodes) { }

    public record PermissionItem(String code, String name) { }

    public record ResetPassword(@NotBlank @Size(min = 6, max = 128) String newPassword) { }

    public record UpdateStatus(@NotBlank String status) { }

    public record BindPhone(@NotBlank @Pattern(regexp = "^1\\d{10}$") String phone,
                            @NotBlank @Size(min = 8, max = 64) String password) { }

    public record RoleItem(String code, String name) { }

    public record Summary(String id, String username, String status,
                          List<String> roleCodes,
                          String lastLoginAt, String createdAt) { }

    public record View(String id, String username, String status,
                       List<RoleItem> roles,
                       String lastLoginAt, String createdAt) { }
}
