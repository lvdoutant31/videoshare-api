package com.yingliu.media.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** OSS 模式下集中绑定并校验存储与 STS 配置；local 模式不要求云端凭据。 */
@Configuration
@EnableConfigurationProperties({OssProperties.class, OssUploadProperties.class})
@ConditionalOnProperty(name = "media.storage-type", havingValue = "oss")
public class MediaStorageAutoConfiguration {
    // OssProperties is enabled via @EnableConfigurationProperties when storage-type=oss.
    // OssStorageService and LocalStorageService self-configure via @ConditionalOnProperty.
}
