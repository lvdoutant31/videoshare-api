package com.yingliu.api.admin.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 装配管理端接口的默认拒绝鉴权。
 *
 * <p>依赖用 {@link ObjectProvider} 获取：本类实现 {@code WebMvcConfigurer}，会被 Web MVC 切片测试
 * （{@code @WebMvcTest}）一起加载，而切片中并不存在业务 Bean。取不到时不注册对应校验，
 * 使切片测试只验证 MVC 契约；真实应用中两个 Bean 始终存在，拦截器按完整规则执行。
 */
@Configuration
public class AdminSaTokenConfiguration implements WebMvcConfigurer {
    private final ObjectProvider<AdminAuthBypassProperties> bypassProperties;
    private final ObjectProvider<AdminAuthorizationProperties> authorizationProperties;
    private final ObjectProvider<AdminPermissionAuthorizer> permissionAuthorizer;

    public AdminSaTokenConfiguration(ObjectProvider<AdminAuthBypassProperties> bypassProperties,
                                     ObjectProvider<AdminAuthorizationProperties> authorizationProperties,
                                     ObjectProvider<AdminPermissionAuthorizer> permissionAuthorizer) {
        this.bypassProperties = bypassProperties;
        this.authorizationProperties = authorizationProperties;
        this.permissionAuthorizer = permissionAuthorizer;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(new AdminAuthInterceptor(
                        bypassProperties.getIfAvailable(AdminAuthBypassProperties::new),
                        authorizationProperties.getIfAvailable(AdminAuthorizationProperties::new),
                        permissionAuthorizer))
                .addPathPatterns("/api/v1/admin/**")
                .addPathPatterns(AdminAuthInterceptor.OSS_UPLOAD_PATHS);
    }
}
