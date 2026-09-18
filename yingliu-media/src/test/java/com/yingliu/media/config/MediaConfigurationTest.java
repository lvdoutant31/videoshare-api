package com.yingliu.media.config;

import com.yingliu.media.controller.SignController;
import com.yingliu.media.storage.OssUploadTool;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证存储模式和配置校验的装配行为，不连接阿里云或本地数据库。 */
class MediaConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(MediaStorageAutoConfiguration.class, OssUploadTool.class, SignController.class);

    @Test
    void localStorageStartsWithoutCloudCredentials() {
        runner.withPropertyValues("media.storage-type=local").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(OssProperties.class);
            assertThat(context).doesNotHaveBean(SignController.class);
        });
    }

    @Test
    void ossModeRejectsIncompleteConfiguration() {
        runner.withPropertyValues("media.storage-type=oss").run(context ->
                assertThat(context).hasFailed());
    }

    @Test
    void ossModeBindsStorageAndStsSettings() {
        configuredRunner().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(SignController.class);
            assertThat(context.getBean(OssProperties.class).bucketName()).isEqualTo("test-bucket");
            assertThat(context.getBean(OssUploadProperties.class).signatureExpireSeconds()).isEqualTo(3600L);
        });
    }

    @Test
    void ossModeRejectsNonpositiveSignatureLifetime() {
        configuredRunner().withPropertyValues("media.oss.signature-expire-seconds=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void serverUploadConfigurationDoesNotRequireStsRole() {
        configuredRunner().withPropertyValues("media.oss.role-arn=").run(context -> {
            assertThat(context).hasNotFailed();
            org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                    context.getBean(OssUploadTool.class).getCredential())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("OSS_STS_ROLE_ARN");
        });
    }

    private ApplicationContextRunner configuredRunner() {
        return runner.withPropertyValues("media.storage-type=oss",
                "media.oss.endpoint=oss-cn-shanghai.aliyuncs.com",
                "media.oss.access-key-id=test-id", "media.oss.access-key-secret=test-secret",
                "media.oss.bucket-name=test-bucket", "media.oss.callback-url=https://api.example/callback",
                "media.oss.role-arn=test-role");
    }
}
