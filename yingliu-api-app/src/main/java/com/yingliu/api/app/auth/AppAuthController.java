package com.yingliu.api.app.auth;

import com.yingliu.api.app.security.AppActorProvider;
import com.yingliu.common.api.ApiResponse;
import com.yingliu.framework.web.TraceIdFilter;
import com.yingliu.service.app.AppAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.yingliu.wecom.WecomProperties;
import com.yingliu.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.CookieValue;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@ConditionalOnBean(AppAuthService.class)
@RequestMapping("/api/v1/app")
public class AppAuthController {
    private final AppAuthService service;
    private final AppActorProvider actor;
    private final WecomProperties wecom;
    private final boolean phoneLoginEnabled;
    private final long sessionTimeoutSeconds;
    private static final String STATE_COOKIE = "yingliu-wecom-state";

    public AppAuthController(AppAuthService service, AppActorProvider actor, WecomProperties wecom,
            @Value("${yingliu.app.phone-login-enabled:false}") boolean phoneLoginEnabled,
            @Value("${yingliu.app.session-timeout-seconds:604800}") long sessionTimeoutSeconds) {
        this.service = service;
        this.actor = actor;
        this.wecom = wecom;
        this.phoneLoginEnabled = phoneLoginEnabled;
        this.sessionTimeoutSeconds = Math.max(3600, sessionTimeoutSeconds);
    }

    @PostMapping("/auth/phone/send-code")
    public ApiResponse<AppAuthDtos.SendCodeResponse> sendCode(@Valid @RequestBody SendCodeBody body, HttpServletRequest request) {
        requirePhoneLoginEnabled(phoneLoginEnabled);
        int expiresIn = service.sendPhoneCode(body.phone(), clientIp(request));
        return ApiResponse.ok(new AppAuthDtos.SendCodeResponse(expiresIn), traceId(request));
    }

    @PostMapping("/auth/phone/login")
    public ApiResponse<AppAuthDtos.PhoneLoginResponse> login(@Valid @RequestBody LoginBody body, HttpServletRequest request) {
        requirePhoneLoginEnabled(phoneLoginEnabled);
        AppAuthService.LoginResult result = service.loginByPhone(body.phone(), body.code(), body.agreementVersion());
        actor.login(result.userId());
        return ApiResponse.ok(new AppAuthDtos.PhoneLoginResponse(actor.currentToken(), sessionTimeoutSeconds,
                service.getMe(result.userId())), traceId(request));
    }

    @PostMapping("/auth/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        actor.logout();
        return ApiResponse.ok(null, traceId(request));
    }

    @GetMapping("/me")
    public ApiResponse<com.yingliu.service.app.support.AppUserView> me(HttpServletRequest request) {
        return ApiResponse.ok(service.getMe(actor.currentUserId()), traceId(request));
    }

    @PatchMapping("/me")
    public ApiResponse<com.yingliu.service.app.support.AppUserView> updateMe(@Valid @RequestBody AppAuthDtos.UpdateMeRequest body,
                                                                             HttpServletRequest request) {
        return ApiResponse.ok(service.updateMe(actor.currentUserId(), body.nickname(), body.bio()), traceId(request));
    }

    @GetMapping("/me/agreements")
    public ApiResponse<List<AppAuthDtos.AgreementResponse>> agreements(HttpServletRequest request) {
        List<AppAuthDtos.AgreementResponse> views = service.getAgreements(actor.currentUserId()).stream()
                .map(v -> new AppAuthDtos.AgreementResponse(v.type(), v.version(), v.agreedAt(), v.currentVersion()))
                .toList();
        return ApiResponse.ok(views, traceId(request));
    }

    @PutMapping("/me/agreements/{type}")
    public ApiResponse<Void> acceptAgreement(@PathVariable String type, HttpServletRequest request) {
        service.acceptAgreement(actor.currentUserId(), type);
        return ApiResponse.ok(null, traceId(request));
    }

    @PostMapping("/me/cancellation")
    public ApiResponse<Void> cancellation(HttpServletRequest request) {
        service.requestCancellation(actor.currentUserId());
        return ApiResponse.ok(null, traceId(request));
    }

    @GetMapping("/auth/wecom/authorize-url")
    public ApiResponse<AppAuthService.WecomAuthorizeResult> wecomAuthorizeUrl(HttpServletRequest request, HttpServletResponse response) {
        var result = service.getWecomAuthorizeUrl();
        response.setHeader("Cache-Control", "no-store");
        response.addHeader("Set-Cookie", stateCookie(result.state(), wecom.stateTtlSeconds()));
        return ApiResponse.ok(result, traceId(request));
    }

    @PostMapping("/auth/wecom/callback")
    public ApiResponse<AppAuthDtos.PhoneLoginResponse> wecomCallback(@Valid @RequestBody WecomCallbackBody body,
            @CookieValue(name = STATE_COOKIE, required = false) String browserState,
            HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        if (browserState == null || !MessageDigest.isEqual(browserState.getBytes(StandardCharsets.UTF_8),
                body.state().getBytes(StandardCharsets.UTF_8))) {
            throw new BusinessException("WECOM_STATE_INVALID", HttpStatus.UNAUTHORIZED, "授权来源不匹配，请重新授权");
        }
        response.addHeader("Set-Cookie", stateCookie("", 0));
        AppAuthService.LoginResult result = service.wecomCallback(body.code(), body.state());
        actor.login(result.userId());
        return ApiResponse.ok(new AppAuthDtos.PhoneLoginResponse(actor.currentToken(), sessionTimeoutSeconds,
                service.getMe(result.userId())), traceId(request));
    }

    static void requirePhoneLoginEnabled(boolean enabled) {
        if (!enabled) {
            throw new BusinessException("AUTH_METHOD_DISABLED", HttpStatus.NOT_FOUND, "手机号登录未启用");
        }
    }

    private String stateCookie(String value, long maxAge) {
        return ResponseCookie.from(STATE_COOKIE, value).httpOnly(true).secure(wecom.cookieSecure())
                .sameSite("Lax").path("/api/v1/app/auth/wecom").maxAge(maxAge).build().toString();
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String traceId(HttpServletRequest request) {
        return (String) request.getAttribute(TraceIdFilter.ATTRIBUTE);
    }

    // ---- 内联请求体（避免每接口单独 record） ----
    public record SendCodeBody(@NotBlank @Pattern(regexp = "^1\\d{10}$") String phone) { }
    public record LoginBody(@NotBlank @Pattern(regexp = "^1\\d{10}$") String phone,
                            @NotBlank @Size(min = 4, max = 6) String code,
                            String agreementVersion) { }
    public record WecomCallbackBody(@NotBlank @Size(max = 512) String code, @NotBlank @Size(max = 128) String state) { }
}
