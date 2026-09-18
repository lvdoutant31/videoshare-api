package com.yingliu.api.admin.security;

import com.yingliu.common.security.PublicApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 默认拒绝鉴权的放行规则。
 *
 * <p>这里的每一条"放行"都是一个潜在的信息暴露面，因此逐条固定下来：
 * 只有显式标注 {@link PublicApi} 的接口、非控制器处理器，以及本地调试旁路才可匿名访问。
 */
class AdminAuthInterceptorTest {

    @Test
    void requiresAuthenticationForOrdinaryAdminHandlers() throws Exception {
        assertFalse(interceptor(closedBypass()).isAnonymousAllowed(handler(ProbeController.class, "secured")));
    }

    @Test
    void allowsHandlerExplicitlyMarkedPublic() throws Exception {
        assertTrue(interceptor(closedBypass()).isAnonymousAllowed(handler(ProbeController.class, "open")));
    }

    @Test
    void allowsControllerMarkedPublicAsAWhole() throws Exception {
        assertTrue(interceptor(closedBypass()).isAnonymousAllowed(handler(PublicController.class, "anything")));
    }

    @Test
    void allowsNonControllerHandlersSoCorsPreflightIsNotBlocked() {
        assertTrue(interceptor(closedBypass()).isAnonymousAllowed(new Object()));
    }

    @Test
    void localBypassAllowsAnonymousAccessForDebugging() throws Exception {
        MockEnvironment environment = new MockEnvironment().withProperty("unused", "true");
        environment.setActiveProfiles("local");
        AdminAuthBypassProperties bypass = new AdminAuthBypassProperties(environment, true, 9000001L);

        assertTrue(interceptor(bypass).isAnonymousAllowed(handler(ProbeController.class, "secured")));
    }

    @Test
    void bypassStaysClosedOutsideLocalProfile() throws Exception {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        // 即使显式打开 auth-disabled，非 local profile 也必须保持鉴权开启。
        AdminAuthBypassProperties bypass = new AdminAuthBypassProperties(environment, true, 9000001L);

        assertFalse(interceptor(bypass).isAnonymousAllowed(handler(ProbeController.class, "secured")));
    }

    @Test
    void allowsAnonymousHandlersWhenAuthorizationIsDisabled() throws Exception {
        AdminAuthInterceptor interceptor = new AdminAuthInterceptor(closedBypass(),
                new AdminAuthorizationProperties(false), unavailableAuthorizer());

        assertTrue(interceptor.isAnonymousAllowed(handler(ProbeController.class, "secured")));
    }

    @Test
    void requiresOssUploadPermissionForSignatureEndpoints() {
        AdminAuthInterceptor interceptor = interceptor(closedBypass());

        for (String path : AdminAuthInterceptor.OSS_UPLOAD_PATHS) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
            assertEquals("oss-test:upload", interceptor.requiredPermission(request),
                    path + " 允许取得对象存储上传签名，必须要求上传测试权限");
        }
    }

    @Test
    void doesNotAddExtraPermissionRequirementForOrdinaryAdminEndpoints() {
        AdminAuthInterceptor interceptor = interceptor(closedBypass());

        assertNull(interceptor.requiredPermission(
                new MockHttpServletRequest("GET", "/api/v1/admin/videos")));
    }

    private static AdminAuthInterceptor interceptor(AdminAuthBypassProperties bypassProperties) {
        return new AdminAuthInterceptor(bypassProperties, unavailableAuthorizer());
    }

    /**
     * 放行判定用不到权限校验，因此提供一个取不到 Bean 的提供者；
     * 一旦被测路径意外走到权限校验就会立即失败，而不是静默通过。
     */
    private static ObjectProvider<AdminPermissionAuthorizer> unavailableAuthorizer() {
        return new ObjectProvider<>() {
            public AdminPermissionAuthorizer getObject() {
                throw new AssertionError("放行判定不应触发权限校验");
            }

            public AdminPermissionAuthorizer getObject(Object... args) {
                throw new AssertionError("放行判定不应触发权限校验");
            }

            public AdminPermissionAuthorizer getIfAvailable() {
                return null;
            }

            public AdminPermissionAuthorizer getIfUnique() {
                return null;
            }

            public java.util.Iterator<AdminPermissionAuthorizer> iterator() {
                return java.util.List.<AdminPermissionAuthorizer>of().iterator();
            }
        };
    }

    /** 无参构造的旁路配置恒为关闭。 */
    private static AdminAuthBypassProperties closedBypass() {
        return new AdminAuthBypassProperties();
    }

    private static HandlerMethod handler(Class<?> controllerType, String methodName) throws Exception {
        Object controller = controllerType.getDeclaredConstructor().newInstance();
        return new HandlerMethod(controller, controllerType.getDeclaredMethod(methodName));
    }

    @RestController
    static class ProbeController {
        @GetMapping("/api/v1/admin/probe")
        void secured() {
        }

        @PublicApi
        @GetMapping("/api/v1/admin/probe-public")
        void open() {
        }
    }

    @PublicApi
    @RestController
    static class PublicController {
        @GetMapping("/api/v1/admin/public")
        void anything() {
        }
    }
}
