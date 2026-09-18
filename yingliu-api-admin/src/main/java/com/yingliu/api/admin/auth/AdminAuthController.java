package com.yingliu.api.admin.auth;

import com.yingliu.api.admin.log.OperLog;
import com.yingliu.common.api.ApiResponse;
import com.yingliu.common.security.PublicApi;
import com.yingliu.framework.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 管理端登录和会话入口；Cookie 会话与手机端登录域独立。 */
@RestController
@Validated
@RequestMapping("/api/v1/admin/auth")
public class AdminAuthController {
    private static final Logger log = LoggerFactory.getLogger(AdminAuthController.class);

    private final AdminAuthService service;
    private final AdminSessionCookieWriter cookieWriter;

    public AdminAuthController(AdminAuthService service, AdminSessionCookieWriter cookieWriter) {
        this.service = service;
        this.cookieWriter = cookieWriter;
    }

    /** 登录入口必须匿名可达，否则无人能取得首个会话。 */
    @PublicApi
    @PostMapping("/login")
    @OperLog(module = "AUTH", action = "LOGIN", targetType = "SESSION", detail = "登录")
    public ApiResponse<SessionView> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
                                           HttpServletResponse response) {
        String traceId = traceId(request);
        String username = body.username().trim();
        log.info("用户登录切点: stage=request username={} clientIp={} traceId={}", username, request.getRemoteAddr(), traceId);
        SessionView result = view(service.login(username, body.password(), request.getRemoteAddr()));
        cookieWriter.write(response);
        log.info("用户登录成功: stage=response result=success adminId={} username={} roles={} permissions={} expiresIn={} traceId={}",
                result.id(), result.username(), result.roles(), result.permissions(), result.expiresIn(), traceId);
        return ApiResponse.ok(result, traceId);
    }

    @GetMapping("/session")
    public ApiResponse<SessionView> session(HttpServletRequest request) {
        String traceId = traceId(request);
        SessionView result = view(service.currentSession());
        log.info("判断用户是否登录: stage=response result=success adminId={} username={} roles={} permissions={} expiresIn={} traceId={}",
                result.id(), result.username(), result.roles(), result.permissions(), result.expiresIn(), traceId);
        return ApiResponse.ok(result, traceId);
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

    /**
     * 会话视图。
     *
     * @param permissions 权限码，前端据此裁决菜单与路由
     * @param roles       角色码，用于界面展示
     * @param expiresIn   会话剩余有效期（秒）；为 null 表示不适用（本地调试旁路）
     */
    public record SessionView(String id, String username, List<String> permissions, List<String> roles, Long expiresIn) { }
}
