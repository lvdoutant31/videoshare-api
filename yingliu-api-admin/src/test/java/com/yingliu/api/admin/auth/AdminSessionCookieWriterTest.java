package com.yingliu.api.admin.auth;

import cn.dev33.satoken.config.SaCookieConfig;
import cn.dev33.satoken.config.SaTokenConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AdminSessionCookieWriterTest {

    @Test
    void buildsPersistentHttpOnlyCookieFromSaTokenConfiguration() {
        SaTokenConfig config = new SaTokenConfig();
        config.setTokenName("yingliu-admin-session");
        config.setCookie(new SaCookieConfig()
                .setPath("/")
                .setHttpOnly(true)
                .setSecure(true)
                .setSameSite("Lax"));

        String header = AdminSessionCookieWriter.build(config, "session-token", 43200).toString();

        assertThat(header)
                .startsWith("yingliu-admin-session=session-token;")
                .contains("Path=/", "Max-Age=43200", "Secure", "HttpOnly", "SameSite=Lax");
    }
}
