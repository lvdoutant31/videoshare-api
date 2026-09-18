package com.yingliu.api.admin.user;

import com.yingliu.api.admin.log.OperLog;
import com.yingliu.api.admin.content.shared.PageResponse;
import com.yingliu.api.admin.security.AdminPermissionAuthorizer;
import com.yingliu.common.api.ApiResponse;
import com.yingliu.common.exception.BusinessException;
import com.yingliu.framework.web.TraceIdFilter;
import com.yingliu.service.content.shared.ServicePage;
import com.yingliu.service.user.AdminUserManagementService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/** 用户管理 HTTP 入口，将治理命令和操作者身份交由业务用例处理。 */
@RestController
@Validated
@ConditionalOnBean(AdminUserManagementService.class)
@RequestMapping("/api/v1/admin")
public class AdminUserManagementController {
    private final AdminUserManagementService service;
    private final AdminPermissionAuthorizer authorizer;

    public AdminUserManagementController(AdminUserManagementService service, AdminPermissionAuthorizer authorizer) {
        this.service = service;
        this.authorizer = authorizer;
    }

    @GetMapping("/users")
    public ApiResponse<PageResponse<UserManagementDtos.Summary>> list(@RequestParam(defaultValue = "1") @Min(1) long page,
                                                                        @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize,
                                                                        @RequestParam(required = false) String keyword,
                                                                        @RequestParam(required = false) String status,
                                                                        HttpServletRequest request) {
        authorizer.require("user:view");
        ServicePage<AdminUserManagementService.UserSummary> result = service.list(new AdminUserManagementService.UserQuery(page, pageSize, keyword, status));
        return ApiResponse.ok(new PageResponse<>(result.items().stream().map(this::summary).toList(), page, pageSize, result.total()), traceId(request));
    }

    @GetMapping("/users/{userId}")
    public ApiResponse<UserManagementDtos.Detail> get(@PathVariable String userId, HttpServletRequest request) {
        authorizer.require("user:view");
        return ApiResponse.ok(detail(service.get(id(userId))), traceId(request));
    }

    @PostMapping("/users/{userId}/mute")
    @OperLog(module = "USER", action = "MUTE", targetType = "USER", detail = "禁言")
    public ApiResponse<UserManagementDtos.Detail> mute(@PathVariable String userId, @Valid @RequestBody UserManagementDtos.TemporaryPenalty body, HttpServletRequest request) {
        long actor = authorizer.require("user:mute");
        return ApiResponse.ok(detail(service.mute(new AdminUserManagementService.TemporaryPenaltyCommand(actor, id(userId), body.reason(), body.endsAt(), traceId(request)))), traceId(request));
    }

    @PostMapping("/users/{userId}/suspend")
    @OperLog(module = "USER", action = "SUSPEND", targetType = "USER", detail = "临时封禁")
    public ApiResponse<UserManagementDtos.Detail> suspend(@PathVariable String userId, @Valid @RequestBody UserManagementDtos.TemporaryPenalty body, HttpServletRequest request) {
        long actor = authorizer.require("user:suspend");
        return ApiResponse.ok(detail(service.suspend(new AdminUserManagementService.TemporaryPenaltyCommand(actor, id(userId), body.reason(), body.endsAt(), traceId(request)))), traceId(request));
    }

    @PostMapping("/confirmations")
    @OperLog(module = "USER", action = "CONFIRM", targetType = "USER", detail = "发起封禁确认")
    public ApiResponse<UserManagementDtos.ConfirmationView> confirmation(@Valid @RequestBody UserManagementDtos.ConfirmationRequest body, HttpServletRequest request) {
        long actor = authorizer.require("user:ban");
        if (!"USER_BAN".equals(body.action()) || !"USER".equals(body.targetType())) throw validation();
        AdminUserManagementService.Confirmation confirmation = service.issueBanConfirmation(actor, id(body.targetId()));
        return ApiResponse.ok(new UserManagementDtos.ConfirmationView(confirmation.confirmationToken(), time(confirmation.expiresAt())), traceId(request));
    }

    @PostMapping("/users/{userId}/ban")
    @OperLog(module = "USER", action = "BAN", targetType = "USER", detail = "永久封禁")
    public ApiResponse<UserManagementDtos.Detail> ban(@PathVariable String userId, @Valid @RequestBody UserManagementDtos.Ban body, HttpServletRequest request) {
        long actor = authorizer.require("user:ban");
        return ApiResponse.ok(detail(service.ban(new AdminUserManagementService.BanCommand(actor, id(userId), body.reason(), body.confirmationToken(), traceId(request)))), traceId(request));
    }

    @PostMapping("/users/{userId}/penalties/{penaltyId}/revoke")
    @OperLog(module = "USER", action = "REVOKE", targetType = "USER", detail = "撤销处罚")
    public ApiResponse<UserManagementDtos.Detail> revoke(@PathVariable String userId, @PathVariable String penaltyId, @Valid @RequestBody UserManagementDtos.Reason body, HttpServletRequest request) {
        long actor = authorizer.require("user:penalty:revoke");
        return ApiResponse.ok(detail(service.revokePenalty(new AdminUserManagementService.RevokePenaltyCommand(actor, id(userId), id(penaltyId), body.reason(), traceId(request)))), traceId(request));
    }

    @PostMapping("/users/{userId}/wecom-binding/unbind")
    @OperLog(module = "USER", action = "UNBIND", targetType = "USER", detail = "解绑企微")
    public ApiResponse<UserManagementDtos.Detail> unbind(@PathVariable String userId, @Valid @RequestBody UserManagementDtos.Reason body, HttpServletRequest request) {
        long actor = authorizer.require("user:wecom:unbind");
        return ApiResponse.ok(detail(service.unbindWeCom(new AdminUserManagementService.UnbindWeComCommand(actor, id(userId), body.reason(), traceId(request)))), traceId(request));
    }

    private UserManagementDtos.Summary summary(AdminUserManagementService.UserSummary item) { return new UserManagementDtos.Summary(String.valueOf(item.id()), item.nickname(), item.avatarUrl(), item.maskedPhone(), item.status(), item.followerCount(), item.followingCount(), item.violationCount(), time(item.createdAt()), time(item.lastLoginAt()), time(item.muteUntil()), time(item.suspendUntil())); }
    private UserManagementDtos.Detail detail(AdminUserManagementService.UserDetail item) { return new UserManagementDtos.Detail(String.valueOf(item.id()), item.nickname(), item.avatarUrl(), item.maskedPhone(), item.status(), item.followerCount(), item.followingCount(), item.violationCount(), time(item.createdAt()), time(item.lastLoginAt()), time(item.muteUntil()), time(item.suspendUntil()), new UserManagementDtos.WeComBinding(item.weComBinding().bound(), time(item.weComBinding().boundAt()), item.weComBinding().organizationName()), new UserManagementDtos.Devices(item.devices().recentDeviceCount(), time(item.devices().lastActiveAt())), item.penalties().stream().map(p -> new UserManagementDtos.Penalty(String.valueOf(p.id()), p.type(), p.reason(), time(p.startsAt()), time(p.endsAt()), p.active(), time(p.revokedAt()), p.revokedReason(), p.operator())).toList()); }
    private long id(String value) { try { long parsed = Long.parseLong(value); if (parsed <= 0) throw validation(); return parsed; } catch (NumberFormatException exception) { throw validation(); } }
    private BusinessException validation() { return new BusinessException("VALIDATION_FAILED", HttpStatus.UNPROCESSABLE_ENTITY, "ID 或确认请求格式不合法"); }
    private String time(LocalDateTime value) { return value == null ? null : value.toString(); }
    private String traceId(HttpServletRequest request) { return (String) request.getAttribute(TraceIdFilter.ATTRIBUTE); }
}
