package com.yingliu.wecom;

import com.yingliu.common.exception.BusinessException;
import me.chanjar.weixin.cp.api.WxCpOAuth2Service;
import me.chanjar.weixin.cp.api.WxCpService;
import me.chanjar.weixin.cp.api.WxCpUserService;
import me.chanjar.weixin.cp.bean.WxCpOauth2UserInfo;
import me.chanjar.weixin.cp.bean.WxCpUser;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WecomClientTest {
    private WecomProperties properties(boolean enabled, boolean mock) {
        return new WecomProperties(enabled, mock, "ww-test-corp", 1000002, "private-secret",
                "https://video.example.com/h5/login", "12345678901234567890123456789012", 300, true);
    }

    @Test void buildsEncodedAuthorizationUrlWithoutDisclosingSecret() {
        var client = new WecomClient(properties(true, false), new MockEnvironment());
        assertThat(client.authorizationUrl("state123"))
                .startsWith("https://open.weixin.qq.com/connect/oauth2/authorize?")
                .contains("appid=ww-test-corp", "scope=snsapi_base", "state=state123",
                        "redirect_uri=https%3A%2F%2Fvideo.example.com%2Fh5%2Flogin")
                .doesNotContain("private-secret", "identity-key");
        assertThat(properties(true, false).toString()).doesNotContain("private-secret", "1234567890");
    }

    @Test void disabledAndProductionMockCannotAuthorize() {
        assertThatThrownBy(() -> new WecomClient(properties(false, false), false, null).authorizationUrl("state"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("尚未配置");
        assertThatThrownBy(() -> new WecomClient(properties(false, true), true, null).exchange("mock-code"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("生产环境禁止");
    }

    @Test void invalidRedirectOrMissingCredentialsRemainUnavailable() {
        var p = new WecomProperties(true, false, "ww-corp", 1, "secret", "http://evil.test/", "short", 300, true);
        assertThatThrownBy(() -> new WecomClient(p, new MockEnvironment()).authorizationUrl("state"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("尚未配置");
    }

    @Test void onlyExplicitMockCodeWorksInMockMode() {
        var client = new WecomClient(properties(false, true), false, null);
        assertThat(client.exchange("mock-code").userId()).isEqualTo("dev-member-001");
        assertThatThrownBy(() -> client.exchange("other-code")).isInstanceOf(BusinessException.class);
    }

    @Test void acceptsEnterpriseMemberAndRejectsExternalIdentity() throws Exception {
        WxCpService sdk = mock(WxCpService.class);
        WxCpOAuth2Service oauth = mock(WxCpOAuth2Service.class);
        WxCpUserService users = mock(WxCpUserService.class);
        when(sdk.getOauth2Service()).thenReturn(oauth);
        when(sdk.getUserService()).thenReturn(users);
        var client = new WecomClient(properties(true, false), false, sdk);
        when(oauth.getUserInfo("member-code")).thenReturn(new WxCpOauth2UserInfo().setUserId("member001"));
        when(users.getById("member001")).thenReturn(new WxCpUser().setName(" 张三 "));
        var member = client.exchange("member-code");
        assertThat(member.corpId()).isEqualTo("ww-test-corp");
        assertThat(member.displayName()).isEqualTo("张三");
        when(oauth.getUserInfo("external-code")).thenReturn(new WxCpOauth2UserInfo().setOpenId("external"));
        assertThatThrownBy(() -> client.exchange("external-code"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("仅限本企业成员");
    }

    @Test void networkErrorsDoNotExposeTokenOrSecret() throws Exception {
        WxCpService sdk = mock(WxCpService.class);
        WxCpOAuth2Service oauth = mock(WxCpOAuth2Service.class);
        when(sdk.getOauth2Service()).thenReturn(oauth);
        when(oauth.getUserInfo("code")).thenThrow(new IllegalStateException("access_token=private-token"));
        assertThatThrownBy(() -> new WecomClient(properties(true, false), false, sdk).exchange("code"))
                .isInstanceOf(BusinessException.class).hasMessageNotContaining("private-token");
    }
}
