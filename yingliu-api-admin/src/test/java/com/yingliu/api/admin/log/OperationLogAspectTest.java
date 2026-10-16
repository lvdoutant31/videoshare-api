package com.yingliu.api.admin.log;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OperationLogAspectTest {
    @Test
    void redactsCredentialsAndVerificationCodesFromOperationLogParameters() throws Exception {
        OperationLogAspect aspect = new OperationLogAspect(null, null, new ObjectMapper());
        Method sanitize = OperationLogAspect.class.getDeclaredMethod("sanitize", Object.class, String.class);
        sanitize.setAccessible(true);

        JsonNode body = (JsonNode) sanitize.invoke(aspect, Map.of(
                "username", "admin",
                "password", "do-not-store",
                "access_token", "do-not-store-either",
                "verificationCode", "123456",
                "profile", Map.of("clientSecret", "private", "displayName", "管理员")
        ), null);
        JsonNode phone = (JsonNode) sanitize.invoke(aspect, "13800000000", "phone");
        Object queryToken = sanitize.invoke(aspect, "private-token", "accessToken");

        assertThat(body.get("username").asText()).isEqualTo("admin");
        assertThat(body.get("password").asText()).isEqualTo("[已脱敏]");
        assertThat(body.get("access_token").asText()).isEqualTo("[已脱敏]");
        assertThat(body.get("verificationCode").asText()).isEqualTo("[已脱敏]");
        assertThat(body.path("profile").path("clientSecret").asText()).isEqualTo("[已脱敏]");
        assertThat(body.path("profile").path("displayName").asText()).isEqualTo("管理员");
        assertThat(phone.asText()).isEqualTo("13800000000");
        assertThat(queryToken).isEqualTo("[已脱敏]");
    }
}
