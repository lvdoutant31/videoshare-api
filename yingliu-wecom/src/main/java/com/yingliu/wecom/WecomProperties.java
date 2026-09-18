package com.yingliu.wecom;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** 自建应用网页授权配置，Secret 和身份加密密钥仅供后端使用。 */
@Validated
@ConfigurationProperties("yingliu.wecom")
public record WecomProperties(boolean enabled, boolean mockEnabled,
        @DefaultValue("") String corpId, @DefaultValue("0") int agentId,
        @DefaultValue("") String secret, @DefaultValue("") String redirectUri,
        @DefaultValue("") String identityKey,
        @Min(60) @Max(1800) @DefaultValue("300") int stateTtlSeconds,
        @DefaultValue("true") boolean cookieSecure) {
    @Override public String toString() {
        return "WecomProperties[enabled=" + enabled + ", mockEnabled=" + mockEnabled + "]";
    }
}
