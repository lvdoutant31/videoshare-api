package com.yingliu.media.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** OSS 存储与回调配置；仅 OSS 模式校验必填项，密钥由运行环境注入。 */
@Validated
@ConfigurationProperties(prefix = "media.oss")
public record OssProperties(
        @NotBlank String endpoint,
        @NotBlank String accessKeyId,
        @NotBlank String accessKeySecret,
        @NotBlank String    bucketName,
        String urlPrefix,
        @NotBlank String callbackUrl
) {}
