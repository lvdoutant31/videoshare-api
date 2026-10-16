package com.yingliu.api.admin.security;

import cn.dev33.satoken.stp.StpUtil;
import com.yingliu.common.security.PublicApi;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.Set;

/**
 * 管理端接口默认拒绝匿名访问。
 *
 * <p>此前鉴权完全依赖每个处理方法内手写 {@code authorizer.require(...)}，漏写一次接口即对外裸奔，
 * 新增接口的默认状态也不安全。本拦截器把"必须先登录"变成默认行为，只有显式标注
 * {@link PublicApi} 的处理方法（或控制器）才被放行。
 *
 * <p>本地调试旁路在此同样生效，且与 {@link AdminPermissionAuthorizer} 使用同一判定，
 * 避免两处口径不一致造成"某个入口在本地可用、另一个不可用"。
 */
class AdminAuthInterceptor implements HandlerInterceptor {
    private static final Logger log = LoggerFactory.getLogger(AdminAuthInterceptor.class);

    /**
     * OSS 上传签名与中转上传入口。这些路径不在 {@code /api/v1/admin} 之下，且定义在
     * 不依赖本模块、也没有 Sa-Token 依赖的 {@code yingliu-media} 中，无法用注解声明，
     * 因此在此按路径集中要求媒体上传权限，避免未认证请求获取对象存储上传签名。
     */
    static final String[] OSS_UPLOAD_PATHS = {
            "/get_post_signature_for_oss_upload",
            "/api/get_post_signature_for_oss_upload",
            "/api/v1/media/oss/upload",
    };

    private static final String MEDIA_UPLOAD_PERMISSION = "oss-test:upload";
    private static final Set<String> OSS_UPLOAD_PATH_SET = Set.of(OSS_UPLOAD_PATHS);

    private final AdminAuthBypassProperties bypassProperties;
    private final AdminAuthorizationProperties authorizationProperties;
    private final ObjectProvider<AdminPermissionAuthorizer> permissionAuthorizer;

    AdminAuthInterceptor(AdminAuthBypassProperties bypassProperties,
                         ObjectProvider<AdminPermissionAuthorizer> permissionAuthorizer) {
        this(bypassProperties, new AdminAuthorizationProperties(), permissionAuthorizer);
    }

    AdminAuthInterceptor(AdminAuthBypassProperties bypassProperties,
                         AdminAuthorizationProperties authorizationProperties,
                         ObjectProvider<AdminPermissionAuthorizer> permissionAuthorizer) {
        this.bypassProperties = bypassProperties;
        this.authorizationProperties = authorizationProperties;
        this.permissionAuthorizer = permissionAuthorizer;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        log.debug("管理端请求鉴权：开始检查 method={} path={} handlerType={}",
                method, path, handler.getClass().getSimpleName());
        if (isAnonymousAllowed(handler)) {
            log.debug("管理端请求鉴权：匿名接口放行 method={} path={}",
                    method, path);
            return true;
        }
        String tokenName = StpUtil.getTokenName();
        boolean cookiePresent = request.getCookies() != null && Arrays.stream(request.getCookies())
                .anyMatch(cookie -> tokenName.equals(cookie.getName()) && !cookie.getValue().isBlank());
        log.debug("管理端请求鉴权：会话 Cookie 检查 result={} tokenName={} cookieCount={} method={} path={}",
                cookiePresent ? "success" : "missing", tokenName,
                request.getCookies() == null ? 0 : request.getCookies().length, method, path);
        // 未登录时抛出 NotLoginException，由 AuthExceptionAdvice 映射为 401。
        try {
            StpUtil.checkLogin();
            Object loginId = StpUtil.getLoginId();
            log.debug("管理端请求鉴权：会话有效 method={} path={} loginId={}",
                    method, path, loginId);
        } catch (RuntimeException exception) {
            log.debug("管理端请求鉴权：会话无效 reason={} method={} path={}",
                    exception.getClass().getSimpleName(), method, path);
            throw exception;
        }
        String requiredPermission = requiredPermission(request);
        if (requiredPermission != null) {
            log.debug("管理端请求鉴权：检查权限 permission={} method={} path={}",
                    requiredPermission, method, path);
            permissionAuthorizer.getObject().require(requiredPermission);
            log.debug("管理端请求鉴权：权限校验通过 permission={} method={} path={}",
                    requiredPermission, method, path);
        } else {
            log.debug("管理端请求鉴权：无需额外权限 method={} path={}",
                    method, path);
        }
        return true;
    }

    /**
     * 匿名放行判定：默认拒绝，仅本地调试旁路、非控制器处理器、显式公开接口三种情况例外。
     *
     * <p>与 Sa-Token 解耦以便逐条验证放行条件——放行条件写错的代价是接口对外裸奔，
     * 而这条判定本身不需要真实会话即可覆盖。
     */
    boolean isAnonymousAllowed(Object handler) {
        if (!authorizationProperties.enabled()) {
            log.debug("管理端鉴权已关闭，匿名接口检查放行");
            return true;
        }
        if (bypassProperties.enabled()) {
            log.debug("本地调试旁路已启用，匿名接口检查放行");
            return true;
        }
        // 静态资源与 CORS 预检由非控制器处理器处理，参与鉴权会打断跨域预检。
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            log.debug("管理端非 Controller 请求放行 handlerType={}",
                    handler.getClass().getSimpleName());
            return true;
        }
        boolean publicApi = isPublic(handlerMethod);
        log.debug("管理端匿名接口策略 decision={} reason={} handler={}.{}",
                publicApi ? "allow" : "continue",
                publicApi ? "public-api" : "protected-controller",
                handlerMethod.getBeanType().getSimpleName(), handlerMethod.getMethod().getName());
        return publicApi;
    }

    private boolean isPublic(HandlerMethod handlerMethod) {
        return handlerMethod.hasMethodAnnotation(PublicApi.class)
                || AnnotatedElementUtils.hasAnnotation(handlerMethod.getBeanType(), PublicApi.class);
    }

    /** 路径级权限：无法用注解声明的外部模块入口在此集中约束。 */
    String requiredPermission(HttpServletRequest request) {
        return OSS_UPLOAD_PATH_SET.contains(request.getRequestURI()) ? MEDIA_UPLOAD_PERMISSION : null;
    }
}
