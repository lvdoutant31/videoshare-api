package com.yingliu.api.app.security;

import cn.dev33.satoken.stp.StpLogic;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class AppSaTokenConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(AppSaTokenConfiguration.class)
            .withPropertyValues("yingliu.app.session-timeout-seconds=604800");

    @Test
    void keepsAppLogicOutOfTheGlobalStpLogicBeanSlot() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(AppActorProvider.class);
            assertThat(context).doesNotHaveBean(StpLogic.class);
        });
    }
}
