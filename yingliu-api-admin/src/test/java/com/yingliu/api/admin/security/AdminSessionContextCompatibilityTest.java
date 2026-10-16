package com.yingliu.api.admin.security;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("removal")
class AdminSessionContextCompatibilityTest {

    @Test
    void deserializesAuthorizationWrittenByOlderReleases() {
        String json = """
                {
                  "@class": "com.yingliu.api.admin.security.AdminSessionContext$Authorization",
                  "adminId": 9000001,
                  "roles": ["java.util.ArrayList", ["SUPER_ADMIN"]],
                  "permissions": ["java.util.ArrayList", ["dashboard:read"]]
                }
                """;

        var serializer = new GenericJackson2JsonRedisSerializer();
        Object restored = serializer.deserialize(json.getBytes(StandardCharsets.UTF_8));

        assertThat(restored).isInstanceOf(AdminSessionContext.Authorization.class);
        assertThat(serializer.deserialize(serializer.serialize(restored)))
                .isInstanceOf(AdminSessionContext.Authorization.class);
    }
}
