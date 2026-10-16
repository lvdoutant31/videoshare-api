package com.yingliu.api.admin.user;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class UserManagementDtos {
    private UserManagementDtos() { }
    public record TemporaryPenalty(@NotBlank @Size(max = 512) String reason, @NotNull @Future LocalDateTime endsAt) { }
    public record Ban(@NotBlank @Size(max = 512) String reason, @NotBlank String confirmationToken) { }
    public record Reason(@NotBlank @Size(max = 512) String reason) { }
    public record ConfirmationRequest(@NotBlank String action, @NotBlank String targetType, @NotBlank String targetId) { }
    public record ConfirmationView(String confirmationToken, String expiresAt) { }
    public record Summary(String id, String nickname, String avatarUrl, String maskedPhone, String status, long followerCount,
                          long followingCount, int violationCount, String createdAt, String lastLoginAt, String muteUntil, String suspendUntil) { }
    public record WeComBinding(boolean bound, String boundAt, String organizationName) { }
    public record Devices(int recentDeviceCount, String lastActiveAt) { }
    public record Penalty(String id, String type, String reason, String startsAt, String endsAt, boolean active,
                          String revokedAt, String revokedReason, String operator) { }
    public record Detail(String id, String nickname, String avatarUrl, String maskedPhone, String status, long followerCount,
                         long followingCount, int violationCount, String createdAt, String lastLoginAt, String muteUntil,
                         String suspendUntil, WeComBinding weComBinding, Devices devices, List<Penalty> penalties) { }
}
