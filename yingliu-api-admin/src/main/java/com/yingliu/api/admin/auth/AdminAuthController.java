package com.yingliu.api.admin.auth;

import com.yingliu.common.api.ApiResponse;
import com.yingliu.common.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Validated
@RequestMapping("/api/v1/admin/auth")
public class AdminAuthController {
    private final AdminAuthService service;

    public AdminAuthController(AdminAuthService service) {
        this.service = service;
    }

    @PostMapping("/login")
    public ApiResponse<SessionView> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        return ApiResponse.ok(view(service.login(body.username().trim(), body.password())), traceId(request));
    }

    @GetMapping("/session")
    public ApiResponse<SessionView> session(HttpServletRequest request) {
        return ApiResponse.ok(view(service.currentSession()), traceId(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        service.logout();
        return ApiResponse.ok(null, traceId(request));
    }

    private SessionView view(AdminAuthService.AdminSession session) {
        return new SessionView(String.valueOf(session.id()), session.username(), session.permissions());
    }

    private String traceId(HttpServletRequest request) { return (String) request.getAttribute(TraceIdFilter.ATTRIBUTE); }

    public record LoginRequest(@NotBlank @Size(max = 64) String username, @NotBlank @Size(max = 128) String password) { }
    public record SessionView(String id, String username, List<String> permissions) { }
}
