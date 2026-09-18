package com.yingliu.wecom;

import com.yingliu.common.exception.BusinessException;
import me.chanjar.weixin.common.error.WxErrorException;
import me.chanjar.weixin.cp.api.WxCpService;
import me.chanjar.weixin.cp.api.impl.WxCpServiceImpl;
import me.chanjar.weixin.cp.config.impl.WxCpDefaultConfigImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.net.URI;

/** 封装已有 WxJava SDK；应用 token 缓存与过期刷新由 SDK 负责，不向前端暴露。 */
@Component
public class WecomClient {
    private final WecomProperties properties;
    private final boolean production;
    private final WxCpService sdk;

    @Autowired
    public WecomClient(WecomProperties properties, Environment environment) {
        this(properties, environment.acceptsProfiles(Profiles.of("prod")), createSdk(properties));
    }

    WecomClient(WecomProperties properties, boolean production, WxCpService sdk) {
        this.properties = properties;
        this.production = production;
        this.sdk = sdk;
    }

    public String authorizationUrl(String state) {
        requireAvailable();
        if (properties.mockEnabled()) return "/h5/login?wecom=mock&state=" + state;
        return sdk.getOauth2Service().buildAuthorizationUrl(properties.redirectUri(), state, "snsapi_base");
    }

    /** code 仅使用一次；非企业成员（仅 OpenId/外部联系人）不能建立成员会话。 */
    public Member exchange(String code) {
        requireAvailable();
        if (code == null || !code.matches("[A-Za-z0-9_-]{1,512}")) throw invalidCode();
        if (properties.mockEnabled()) {
            if (!"mock-code".equals(code)) throw invalidCode();
            return new Member("local-mock-corp", "dev-member-001", "企业开发成员");
        }
        try {
            var info = sdk.getOauth2Service().getUserInfo(code);
            if (info == null || info.getUserId() == null || info.getUserId().isBlank()) {
                throw new BusinessException("WECOM_MEMBER_REQUIRED", HttpStatus.FORBIDDEN, "仅限本企业成员登录");
            }
            return new Member(properties.corpId(), info.getUserId(), displayName(info.getUserId()));
        } catch (WxErrorException error) {
            int codeValue = error.getError() == null ? -1 : error.getError().getErrorCode();
            if (codeValue == 40029 || codeValue == 40163) throw invalidCode();
            // SDK 异常可能含请求 URL / token，不把原异常或原始 errmsg 写入响应、日志。
            throw unavailable("企业微信授权服务暂不可用，请稍后重新授权");
        } catch (BusinessException error) {
            throw error;
        } catch (RuntimeException error) {
            throw unavailable("企业微信授权请求失败，请稍后重新授权");
        }
    }

    public String identityKey() {
        requireAvailable();
        return properties.mockEnabled() ? "local-wecom-identity-key-0000000000" : properties.identityKey();
    }

    private String displayName(String userId) {
        try {
            var user = sdk.getUserService().getById(userId);
            if (user != null && user.getName() != null && !user.getName().isBlank()) {
                return user.getName().trim();
            }
        } catch (WxErrorException | RuntimeException ignored) {
            // OAuth 已确认成员身份；资料读取失败不阻断登录，也不记录可能含 token 的 SDK 异常。
        }
        return "企业成员";
    }

    private void requireAvailable() {
        if (production && properties.mockEnabled()) throw unavailable("生产环境禁止模拟企业微信登录");
        if (properties.mockEnabled()) return;
        if (!properties.enabled() || sdk == null || !valid(properties)) {
            throw unavailable("企业微信登录尚未配置，请联系管理员");
        }
    }

    private static WxCpService createSdk(WecomProperties p) {
        if (!p.enabled() || p.mockEnabled() || !valid(p)) return null;
        var config = new WxCpDefaultConfigImpl();
        config.setCorpId(p.corpId());
        config.setAgentId(p.agentId());
        config.setCorpSecret(p.secret());
        config.setOauth2redirectUri(p.redirectUri());
        var sdk = new WxCpServiceImpl();
        sdk.setWxCpConfigStorage(config);
        sdk.setMaxRetryTimes(1);
        return sdk;
    }

    private static boolean valid(WecomProperties p) {
        if (p.corpId().isBlank() || p.agentId() <= 0 || p.secret().isBlank() || p.identityKey().length() < 32) return false;
        try {
            URI uri = URI.create(p.redirectUri());
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                    && uri.getUserInfo() == null && uri.getFragment() == null;
        } catch (IllegalArgumentException error) { return false; }
    }

    private static BusinessException invalidCode() {
        return new BusinessException("WECOM_CODE_INVALID", HttpStatus.UNAUTHORIZED, "授权码无效或已使用，请重新授权");
    }
    private static BusinessException unavailable(String message) {
        return new BusinessException("WECOM_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE, message);
    }
    public record Member(String corpId, String userId, String displayName) { }
}
