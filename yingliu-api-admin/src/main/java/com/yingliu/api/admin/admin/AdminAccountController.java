package com.yingliu.api.admin.admin;

import com.yingliu.api.admin.content.shared.PageResponse;
import com.yingliu.api.admin.log.OperLog;
import com.yingliu.api.admin.security.AdminPermissionAuthorizer;
import com.yingliu.api.admin.security.AdminSessionRevoker;
import com.yingliu.common.api.ApiResponse;
import com.yingliu.common.exception.BusinessException;
import com.yingliu.framework.web.TraceIdFilter;
import com.yingliu.domain.content.video.AdminUser;
import com.yingliu.service.admin.AdminAccountService;
import com.yingliu.service.admin.AdminAppAccountBindingService;
import com.yingliu.service.content.shared.ServicePage;
import com.yingliu.service.content.video.mapper.AdminUserMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;

import java.util.List;

/** 管理端账号维护入口，处理账号请求及返回值转换。 */
@RestController
@Validated
@ConditionalOnBean(AdminAccountService.class)
@RequestMapping("/api/v1/admin/admins")
public class AdminAccountController {
    private final AdminAccountService service;
    private final AdminPermissionAuthorizer authorizer;
    private final AdminSessionRevoker sessionRevoker;
    private final AdminAppAccountBindingService appAccountBinding;

    public AdminAccountController(AdminAccountService service, AdminPermissionAuthorizer authorizer,
                                  AdminSessionRevoker sessionRevoker, AdminAppAccountBindingService appAccountBinding) {
        this.service = service;
        this.authorizer = authorizer;
        this.sessionRevoker = sessionRevoker;
        this.appAccountBinding = appAccountBinding;
    }

    @GetMapping("/{adminId}/app-account")
    public ApiResponse<AdminAppAccountBindingService.BindingView> appAccount(
            @PathVariable long adminId, HttpServletRequest request) {
        requireSuperAdmin();
        return ApiResponse.ok(appAccountBinding.get(adminId), traceId(request));
    }

    @PutMapping("/{adminId}/app-account")
    @OperLog(module = "ADMIN", action = "UPDATE", targetType = "ADMIN_ACCOUNT", detail = "绑定手机号")
    public ApiResponse<AdminAppAccountBindingService.BindingView> bindAppAccount(
            @PathVariable long adminId, @Valid @RequestBody AdminAccountDtos.BindPhone body,
            HttpServletRequest request) {
        requireSuperAdmin();
        return ApiResponse.ok(appAccountBinding.bind(adminId, body.phone(), body.password()), traceId(request));
    }

    private void requireSuperAdmin() {
        long operatorId = authorizer.require("admin:view");
        if (!service.getRoleCodes(operatorId).contains("SUPER_ADMIN")) {
            throw new BusinessException("PERMISSION_DENIED", HttpStatus.FORBIDDEN, "仅超级管理员可为其他管理员绑定手机号");
        }
    }

    @GetMapping("/permissions")
    public ApiResponse<List<AdminAccountDtos.PermissionItem>> listPermissions(HttpServletRequest request) {
        authorizer.require("admin:permissions");
        return ApiResponse.ok(service.permissionCatalog().stream()
                .map(p -> new AdminAccountDtos.PermissionItem(p.code(), p.name())).toList(), traceId(request));
    }

    @GetMapping("/{adminId}/permissions")
    public ApiResponse<List<String>> getPermissions(@PathVariable long adminId, HttpServletRequest request) {
        authorizer.require("admin:permissions");
        return ApiResponse.ok(service.getPermissionCodes(adminId), traceId(request));
    }

    @PutMapping("/{adminId}/permissions")
    @OperLog(module = "ADMIN", action = "UPDATE", targetType = "ADMIN_ACCOUNT", detail = "配置管理员权限")
    public ApiResponse<List<String>> updatePermissions(@PathVariable long adminId,
            @Valid @RequestBody AdminAccountDtos.UpdatePermissions body, HttpServletRequest request) {
        authorizer.require("admin:permissions");
        service.updatePermissions(adminId, body.permissionCodes());
        sessionRevoker.refreshGrants();
        return ApiResponse.ok(service.getPermissionCodes(adminId), traceId(request));
    }

    @GetMapping("/roles")
    public ApiResponse<List<AdminAccountDtos.RoleItem>> listRoles(HttpServletRequest request) {
        authorizer.require("admin:view");
        List<AdminUserMapper.RoleView> roles = service.listAllActiveRoles();
        return ApiResponse.ok(roles.stream().map(r -> new AdminAccountDtos.RoleItem(r.roleCode(), r.roleName())).toList(), traceId(request));
    }

    @GetMapping
    public ApiResponse<PageResponse<AdminAccountDtos.Summary>> list(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            HttpServletRequest request) {
        authorizer.require("admin:view");
        ServicePage<AdminUser> result = service.list(page, pageSize, keyword, status);
        return ApiResponse.ok(new PageResponse<>(
                result.items().stream().map(this::summary).toList(),
                page, pageSize, result.total()), traceId(request));
    }

    @GetMapping("/{adminId}")
    public ApiResponse<AdminAccountDtos.View> get(@PathVariable long adminId, HttpServletRequest request) {
        authorizer.require("admin:view");
        AdminUser admin = service.get(adminId);
        return ApiResponse.ok(view(admin), traceId(request));
    }

    @PostMapping
    @OperLog(module = "ADMIN", action = "CREATE", targetType = "ADMIN_ACCOUNT", detail = "创建管理员")
    public ResponseEntity<ApiResponse<AdminAccountDtos.View>> create(
            @Valid @RequestBody AdminAccountDtos.Create body, HttpServletRequest request) {
        authorizer.require("admin:create");
        AdminUser admin = service.create(body.username(), body.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(view(admin), traceId(request)));
    }

    @PostMapping("/{adminId}/password")
    @OperLog(module = "ADMIN", action = "UPDATE", targetType = "ADMIN_ACCOUNT", detail = "重置密码")
    public ApiResponse<Void> resetPassword(
            @PathVariable long adminId, @Valid @RequestBody AdminAccountDtos.ResetPassword body,
            HttpServletRequest request) {
        authorizer.require("admin:password");
        service.resetPassword(adminId, body.newPassword());
        // 凭据已变更：立即断开该账号所有会话，旧密码换来的会话不能继续使用。
        sessionRevoker.revoke(adminId);
        return ApiResponse.ok(null, traceId(request));
    }

    @PatchMapping("/{adminId}/status")
    @OperLog(module = "ADMIN", action = "UPDATE", targetType = "ADMIN_ACCOUNT", detail = "启停管理员")
    public ApiResponse<AdminAccountDtos.View> updateStatus(
            @PathVariable long adminId, @Valid @RequestBody AdminAccountDtos.UpdateStatus body,
            HttpServletRequest request) {
        authorizer.require("admin:status");
        AdminUser admin = service.updateStatus(adminId, body.status());
        if ("DISABLED".equals(admin.getStatus())) {
            // 停用必须即时生效，不能等到会话自然过期。
            sessionRevoker.revoke(adminId);
        } else {
            sessionRevoker.refreshGrants();
        }
        return ApiResponse.ok(view(admin), traceId(request));
    }

    /** 强制下线：目标账号的在线会话立即失效，用户下次请求会收到"账号已在其他设备登录"。 */
    @PostMapping("/{adminId}/kickout")
    @OperLog(module = "ADMIN", action = "KICKOUT", targetType = "ADMIN_ACCOUNT", detail = "强制下线管理员")
    public ApiResponse<Void> kickout(@PathVariable long adminId, HttpServletRequest request) {
        authorizer.require("admin:status");
        service.get(adminId);
        sessionRevoker.kickout(adminId);
        return ApiResponse.ok(null, traceId(request));
    }

    private AdminAccountDtos.Summary summary(AdminUser admin) {
        List<String> roleCodes = service.getRoleCodes(admin.getId());
        return new AdminAccountDtos.Summary(
                String.valueOf(admin.getId()), admin.getUsername(), admin.getStatus(),
                roleCodes,
                admin.getLastLoginAt() == null ? null : admin.getLastLoginAt().toString(),
                admin.getCreatedAt() == null ? null : admin.getCreatedAt().toString());
    }

    private AdminAccountDtos.View view(AdminUser admin) {
        List<AdminUserMapper.RoleView> roles = service.getRoles(admin.getId());
        return new AdminAccountDtos.View(
                String.valueOf(admin.getId()), admin.getUsername(), admin.getStatus(),
                roles.stream().map(r -> new AdminAccountDtos.RoleItem(r.roleCode(), r.roleName())).toList(),
                admin.getLastLoginAt() == null ? null : admin.getLastLoginAt().toString(),
                admin.getCreatedAt() == null ? null : admin.getCreatedAt().toString());
    }

    private String traceId(HttpServletRequest request) {
        return (String) request.getAttribute(TraceIdFilter.ATTRIBUTE);
    }
}
