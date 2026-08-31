package com.yingliu.service.user;

import com.yingliu.service.content.shared.ServicePage;

import java.time.LocalDateTime;
import java.util.List;

public interface AdminUserManagementService {
    ServicePage<UserSummary> list(UserQuery query);
    UserDetail get(long userId);
    UserDetail mute(TemporaryPenaltyCommand command);
    UserDetail suspend(TemporaryPenaltyCommand command);
    Confirmation issueBanConfirmation(long actorAdminId, long userId);
    UserDetail ban(BanCommand command);
    UserDetail revokePenalty(RevokePenaltyCommand command);
    UserDetail unbindWeCom(UnbindWeComCommand command);

    record UserQuery(long page, long pageSize, String keyword, String status) { }
    record TemporaryPenaltyCommand(long actorAdminId, long userId, String reason, LocalDateTime endsAt, String traceId) { }
    record BanCommand(long actorAdminId, long userId, String reason, String confirmationToken, String traceId) { }
    record RevokePenaltyCommand(long actorAdminId, long userId, long penaltyId, String reason, String traceId) { }
    record UnbindWeComCommand(long actorAdminId, long userId, String reason, String traceId) { }
    record Confirmation(String confirmationToken, LocalDateTime expiresAt) { }
    record UserSummary(long id, String nickname, String avatarUrl, String maskedPhone, String status,
                       long followerCount, long followingCount, int violationCount, LocalDateTime createdAt,
                       LocalDateTime lastLoginAt, LocalDateTime muteUntil, LocalDateTime suspendUntil) { }
    record WeComBindingView(boolean bound, LocalDateTime boundAt, String organizationName) { }
    record DeviceSummary(int recentDeviceCount, LocalDateTime lastActiveAt) { }
    record PenaltyView(long id, String type, String reason, LocalDateTime startsAt, LocalDateTime endsAt,
                       boolean active, LocalDateTime revokedAt, String revokedReason, String operator) { }
    record UserDetail(long id, String nickname, String avatarUrl, String maskedPhone, String status,
                      long followerCount, long followingCount, int violationCount, LocalDateTime createdAt,
                      LocalDateTime lastLoginAt, LocalDateTime muteUntil, LocalDateTime suspendUntil,
                      WeComBindingView weComBinding, DeviceSummary devices, List<PenaltyView> penalties) { }
}
