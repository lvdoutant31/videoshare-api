package com.yingliu.framework.web;

import cn.dev33.satoken.exception.DisableServiceException;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import com.yingliu.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 鉴权异常到 HTTP 401/403 稳定错误码的映射。
 *
 * <p>必须早于 {@link GlobalExceptionHandler} 生效：后者的 {@code @ExceptionHandler(Exception.class)}
 * 兜底分支会把"未登录"当成未知异常返回 500，前端因此收不到 401，也就无法跳转登录页。
 * 本类只依赖 Sa-Token 的异常类型，不依赖任何会话实现。
 *
 * <p>错误码契约（前端据此决定是否跳登录页）：
 * <ul>
 *   <li>未携带凭证 / 凭证格式错误 → 401 {@code AUTH_REQUIRED}</li>
 *   <li>凭证无效、过期、被顶下线、被踢、被冻结 → 401 {@code TOKEN_EXPIRED}（区分文案）</li>
 *   <li>已登录但缺少权限 → 403 {@code PERMISSION_DENIED}（前端不应跳登录）</li>
 *   <li>账号被禁用 → 403 {@code ACCOUNT_DISABLED}</li>
 * </ul>
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnClass(NotLoginException.class)
public class AuthExceptionAdvice {

    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotLogin(NotLoginException exception, HttpServletRequest request) {
        String type = exception.getType();
        // 未携带凭证与凭证前缀错误都视为"尚未登录"；其余一律按"登录已失效"处理，
        // 让前端只需区分两个状态：引导登录，还是提示已过期后重新登录。
        boolean noCredential = NotLoginException.NOT_TOKEN.equals(type)
                || NotLoginException.NO_PREFIX.equals(type);
        String code = noCredential ? "AUTH_REQUIRED" : "TOKEN_EXPIRED";
        return response(HttpStatus.UNAUTHORIZED, code, messageOf(type), request);
    }

    @ExceptionHandler({NotPermissionException.class, NotRoleException.class})
    public ResponseEntity<ApiResponse<Void>> handleNotPermission(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.FORBIDDEN, "PERMISSION_DENIED", "无权执行此操作", request);
    }

    @ExceptionHandler(DisableServiceException.class)
    public ResponseEntity<ApiResponse<Void>> handleDisabled(DisableServiceException exception, HttpServletRequest request) {
        return response(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "账号已被禁用", request);
    }

    private String messageOf(String type) {
        if (NotLoginException.NOT_TOKEN.equals(type) || NotLoginException.NO_PREFIX.equals(type)) {
            return "请先登录";
        }
        if (NotLoginException.TOKEN_TIMEOUT.equals(type) || NotLoginException.INVALID_TOKEN.equals(type)) {
            return "登录已过期，请重新登录";
        }
        if (NotLoginException.BE_REPLACED.equals(type)) {
            return "账号已在其他设备登录";
        }
        if (NotLoginException.KICK_OUT.equals(type)) {
            return "账号已被强制下线";
        }
        if (NotLoginException.TOKEN_FREEZE.equals(type)) {
            return "账号已被冻结";
        }
        return "登录状态无效，请重新登录";
    }

    private ResponseEntity<ApiResponse<Void>> response(HttpStatus status, String code, String message,
                                                       HttpServletRequest request) {
        String traceId = (String) request.getAttribute(TraceIdFilter.ATTRIBUTE);
        return ResponseEntity.status(status).body(ApiResponse.fail(code, message, traceId));
    }
}
