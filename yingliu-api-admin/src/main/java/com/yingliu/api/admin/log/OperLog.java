package com.yingliu.api.admin.log;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a controller method for automatic operation logging.
 * The AOP aspect reads these values to populate the log record.
 * If not present, the aspect infers module/action from the controller class and method name.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperLog {
    /** Module name, e.g. "VIDEO", "REVIEW", "TAXONOMY" */
    String module() default "";
    /** Action name, e.g. "CREATE", "PUBLISH", "OFF_SHELF" */
    String action() default "";
    /** Target entity type, e.g. "VIDEO", "CATEGORY". Defaults to module if empty. */
    String targetType() default "";
    /** Human-readable detail, e.g. "下架原因：内容需要更新". Supports SpEL not needed — plain text. */
    String detail() default "";
}
