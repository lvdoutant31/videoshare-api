package com.yingliu.api.app.auth;

import com.yingliu.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppAuthControllerTest {

    @Test
    void phoneAuthenticationIsUnavailableWhenWecomIsTheOnlyUserSource() {
        assertThatThrownBy(() -> AppAuthController.requirePhoneLoginEnabled(false))
                .isInstanceOf(BusinessException.class)
                .hasMessage("手机号登录未启用");
    }
}
