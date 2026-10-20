package com.yingliu.api.admin.auth;

import com.yingliu.api.admin.log.OperLog;
import com.yingliu.common.api.ApiResponse;
import com.yingliu.common.exception.BusinessException;
import com.yingliu.common.security.PublicApi;
import com.yingliu.service.admin.AdminAppAccountBindingService;
import com.yingliu.framework.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 管理端登录和会话入口；Cookie 会话与手机端登录域独立。 */
@RestController
@Validated
@RequestMapping("/api/v1/admin/auth")
public class AdminAuthController {
    private final AdminAuthService service;
    private final AdminSessionCookieWriter cookieWriter;
    private final AdminAppAccountBindingService appAccountBinding;

    public AdminAuthController(AdminAuthService service, AdminSessionCookieWriter cookieWriter,
                               AdminAppAccountBindingService appAccountBinding) {
        this.service = service;
        this.cookieWriter = cookieWriter;
        this.appAccountBinding = appAccountBinding;
    }

    /** 登录入口必须匿名可达，否则无人能取得首个会话。 */
    @PublicApi
    @PostMapping("/login")
    @OperLog(module = "AUTH", action = "LOGIN", targetType = "SESSION", detail = "登录")
    public ApiResponse<SessionView> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
                                           HttpServletResponse response) {
        if (body == null) {
            throw new BusinessException("CRYPTO_INVALID_REQUEST", org.springframework.http.HttpStatus.BAD_REQUEST, "请求参数无效");
        }
        String username = body.username().trim();
        SessionView result = view(service.login(username, body.password(), request.getRemoteAddr()));
        cookieWriter.write(response);
        return ApiResponse.ok(result, traceId(request));
    }

    @GetMapping("/session")
    public ApiResponse<SessionView> session(HttpServletRequest request) {
        String traceId = traceId(request);
        return ApiResponse.ok(view(service.currentSession()), traceId);
    }

    @GetMapping("/app-account")
    public ApiResponse<AdminAppAccountBindingService.BindingView> appAccount(HttpServletRequest request) {
        return ApiResponse.ok(appAccountBinding.get(service.currentSession().id()), traceId(request));
    }

    @PutMapping("/app-account")
    @OperLog(module = "AUTH", action = "UPDATE", targetType = "APP_ACCOUNT", detail = "绑定手机号")
    public ApiResponse<AdminAppAccountBindingService.BindingView> bindAppAccount(
            @Valid @RequestBody AppAccountBinding body, HttpServletRequest request) {
        return ApiResponse.ok(appAccountBinding.bind(service.currentSession().id(), body.phone(), body.password()),
                traceId(request));
    }

    @PostMapping("/logout")
    @OperLog(module = "AUTH", action = "LOGOUT", targetType = "SESSION", detail = "登出")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        service.logout();
        return ApiResponse.ok(null, traceId(request));
    }

    private SessionView view(AdminAuthService.AdminSession session) {
        return new SessionView(String.valueOf(session.id()), session.username(), session.permissions(),
                session.roles(), session.expiresIn());
    }

    private String traceId(HttpServletRequest request) { return (String) request.getAttribute(TraceIdFilter.ATTRIBUTE); }

    public record LoginRequest(@NotBlank @Size(max = 64) String username, @NotBlank @Size(max = 128) String password) { }

    public record AppAccountBinding(@NotBlank @Pattern(regexp = "^1\\d{10}$") String phone,
                                    @NotBlank @Size(min = 8, max = 64) String password) { }

    /**
     * 会话视图。
     *
     * @param permissions 权限码，前端据此裁决菜单与路由
     * @param roles       角色码，用于界面展示
     * @param expiresIn   会话剩余有效期（秒）；为 null 表示不适用（本地调试旁路）
     */
    public record SessionView(String id, String username, List<String> permissions, List<String> roles, Long expiresIn) { }
}
