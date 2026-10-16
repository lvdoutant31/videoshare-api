package com.yingliu.common.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记无需登录即可访问的接口。
 *
 * <p>管理端接口默认拒绝匿名访问：位于受保护前缀下的请求必须先通过登录校验，
 * 只有显式标注本注解的处理方法（或控制器）才被放行。注解写在使用现场，
 * 使"公开边界"与路由同处一个文件，避免在配置里维护路径清单而与之漂移。
 *
 * <p>仅供确实不需要身份的场景使用，例如登录入口与探活接口。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PublicApi {
}
