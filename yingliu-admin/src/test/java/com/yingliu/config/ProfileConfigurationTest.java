package com.yingliu.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/** 从启动模块真实 YAML 验证 profile 覆盖顺序与共享配置，无需启动数据库。 */
class ProfileConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer());

    @Test
    void productionHasSharedStsSettingsAndDisablesAuthBypass() {
        runner.withPropertyValues("spring.profiles.active=prod", "MYSQL_PASSWORD=test-password",
                "OSS_STS_ROLE_ARN=test-role", "OSS_REGION=cn-shanghai",
                "YINGLIU_ADMIN_AUTH_DISABLED=true").run(context -> {
            assertThat(context).hasNotFailed();
            var env = context.getEnvironment();
            assertThat(env.getProperty("logging.level.me.chanjar.weixin")).isEqualToIgnoringCase("OFF");
            assertThat(env.getProperty("yingliu.admin.auth-disabled", Boolean.class)).isFalse();
            assertThat(env.getProperty("yingliu.wecom.mock-enabled", Boolean.class)).isFalse();
            assertThat(env.getProperty("yingliu.wecom.cookie-secure", Boolean.class)).isTrue();
            assertThat(env.getProperty("yingliu.app.phone-login-enabled", Boolean.class)).isFalse();
            assertThat(env.getProperty("yingliu.app.session-timeout-seconds", Long.class)).isEqualTo(604800L);
            assertThat(env.getProperty("media.oss.role-arn")).isEqualTo("test-role");
            assertThat(env.getProperty("media.oss.region")).isEqualTo("cn-shanghai");
            assertThat(env.getProperty("spring.servlet.multipart.max-file-size")).isEqualTo("500MB");
            assertThat(env.getProperty("spring.config.import")).isEqualTo("classpath:application-wecom.yml");
        });
    }

    /**
     * 管理端会话与风控属于安全基线，不能被配置漂移静默放宽。
     *
     * <p>此前的实际取值是 Sa-Token 默认的 30 天有效期、无空闲下线，
     * 且令牌可从请求体与请求头读取。
     */
    @Test
    void productionPinsAdminSessionAndLoginGuardBaseline() {
        runner.withPropertyValues("spring.profiles.active=prod", "MYSQL_PASSWORD=test-password",
                "OSS_STS_ROLE_ARN=test-role", "OSS_REGION=cn-shanghai").run(context -> {
            assertThat(context).hasNotFailed();
            var env = context.getEnvironment();
            assertThat(env.getProperty("sa-token.token-name")).isEqualTo("yingliu-admin-session");
            assertThat(env.getProperty("sa-token.timeout", Long.class)).isEqualTo(43200L);
            assertThat(env.getProperty("sa-token.active-timeout", Long.class)).isEqualTo(1800L);
            assertThat(env.getProperty("sa-token.is-concurrent", Boolean.class)).isFalse();
            assertThat(env.getProperty("sa-token.is-share", Boolean.class)).isFalse();
            assertThat(env.getProperty("sa-token.is-read-cookie", Boolean.class)).isTrue();
            assertThat(env.getProperty("sa-token.is-read-header", Boolean.class)).isFalse();
            assertThat(env.getProperty("sa-token.is-read-body", Boolean.class)).isFalse();
            assertThat(env.getProperty("yingliu.auth.login.max-failures", Integer.class)).isEqualTo(5);
            assertThat(env.getProperty("yingliu.auth.login.lock-seconds", Integer.class)).isEqualTo(900);
            assertThat(env.getProperty("yingliu.admin.auth-disabled", Boolean.class)).isFalse();
        });
    }

    @Test
    void localUsesCentralWecomSwitchAndAllowsExplicitMock() {        runner.withPropertyValues("spring.profiles.active=local", "MYSQL_PASSWORD=test-password").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getEnvironment().getProperty("yingliu.wecom.mock-enabled", Boolean.class)).isFalse();
        });
        runner.withPropertyValues("spring.profiles.active=local", "MYSQL_PASSWORD=test-password",
                "WECOM_MOCK_ENABLED=true").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getEnvironment().getProperty("yingliu.wecom.mock-enabled", Boolean.class)).isTrue();
        });
    }

    @Test
    void testProfileDoesNotImportLocalSecretsOrActivateCloudStorage() {
        runner.withPropertyValues("spring.profiles.active=test").run(context -> {
            assertThat(context).hasNotFailed();
            var env = context.getEnvironment();
            assertThat(env.getProperty("logging.level.me.chanjar.weixin")).isEqualToIgnoringCase("OFF");
            assertThat(env.getProperty("spring.config.import")).isEqualTo("classpath:application-wecom.yml");
            assertThat(env.getProperty("media.storage-type")).isEqualTo("local");
            assertThat(env.getProperty("spring.autoconfigure.exclude[0]"))
                    .isEqualTo("org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration");
        });
    }
}
