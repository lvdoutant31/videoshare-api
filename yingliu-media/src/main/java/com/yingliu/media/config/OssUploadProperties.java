package com.yingliu.media.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** OSS 直传的 STS 与签名配置；仅在 OSS 模式下绑定校验，过期时间单位为秒。 */
@Validated
@ConfigurationProperties(prefix = "media.oss")
public record OssUploadProperties(
        /* 后端 OSS SDK 上传不依赖 STS；旧直传接口在调用时检查 Role ARN。 */
        String roleArn,
        @NotBlank @DefaultValue("cn-wuhan-lr") String region,
        @NotBlank @DefaultValue("sts.cn-hangzhou.aliyuncs.com") String stsEndpoint,
        @DefaultValue("dir/") String uploadDir,
        @Positive @DefaultValue("3600") long signatureExpireSeconds
) { }
