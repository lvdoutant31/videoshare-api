package com.yingliu.service.app;

import com.yingliu.common.exception.BusinessException;
import com.yingliu.common.security.PhoneCrypto;
import com.yingliu.domain.user.AppUser;
import com.yingliu.domain.user.management.UserWeComIdentity;
import com.yingliu.service.app.mapper.AppUserMapper;
import com.yingliu.service.app.support.VerificationCodeStore;
import com.yingliu.service.user.mapper.UserWeComIdentityMapper;
import com.yingliu.wecom.WecomClient;
import com.yingliu.wecom.WecomProperties;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;

/** 授权码交换在事务外完成；成员身份和平台账号在一个事务中绑定。 */
@Service
@org.springframework.context.annotation.Profile("!test")
public class WecomLoginService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String STATE_PREFIX = "yingliu:wecom:state:";
    private final WecomClient client;
    private final WecomProperties properties;
    private final VerificationCodeStore states;
    private final AppUserMapper users;
    private final UserWeComIdentityMapper identities;
    private final TransactionTemplate transactions;

    public WecomLoginService(WecomClient client, WecomProperties properties, VerificationCodeStore states,
            AppUserMapper users, UserWeComIdentityMapper identities, TransactionTemplate transactions) {
        this.client = client; this.properties = properties; this.states = states;
        this.users = users; this.identities = identities; this.transactions = transactions;
    }

    public AppAuthService.WecomAuthorizeResult authorize() {
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        String state = HexFormat.of().formatHex(bytes);
        String url = client.authorizationUrl(state);
        states.set(STATE_PREFIX + state, "pending", properties.stateTtlSeconds());
        return new AppAuthService.WecomAuthorizeResult(url, state);
    }

    public AppAuthService.LoginResult login(String code, String state) {
        if (state == null || !state.matches("[a-f0-9]{64}") || states.consume(STATE_PREFIX + state) == null) {
            throw new BusinessException("WECOM_STATE_INVALID", HttpStatus.UNAUTHORIZED, "授权已过期或已使用，请重新授权");
        }
        var member = client.exchange(code);
        String key = client.identityKey();
        try {
            return transactions.execute(status -> bind(member, key));
        } catch (DuplicateKeyException error) {
            // 并发首次登录由数据库唯一约束兜底，失败事务回滚，不留下多余账号。
            throw new BusinessException("WECOM_LOGIN_RETRY", HttpStatus.CONFLICT, "登录状态已更新，请重新授权");
        }
    }

    private AppAuthService.LoginResult bind(WecomClient.Member member, String key) {
        String hmac = PhoneCrypto.hmac(member.corpId() + ":" + member.userId(), key);
        UserWeComIdentity identity = identities.findMember(member.corpId(), hmac);
        AppUser user;
        if (identity == null) {
            user = new AppUser();
            user.setNickname(member.displayName()); user.setStatus("NORMAL"); user.setDeleted(0);
            user.setViolationCount(0); user.setLikeReceivedCount(0L);
            user.setFollowingCount(0L); user.setFollowerCount(0L);
            users.insert(user);
            identity = new UserWeComIdentity();
            identity.setUserId(user.getId()); identity.setCorpId(member.corpId());
            identity.setWecomUserIdHmac(hmac);
            identity.setWecomUserIdCiphertext(PhoneCrypto.encrypt(member.userId(), key));
            identity.setDisplayName(member.displayName()); identity.setBindStatus("ACTIVE");
            identity.setBoundAt(LocalDateTime.now()); identity.setCreatedAt(LocalDateTime.now());
            identity.setUpdatedAt(LocalDateTime.now()); identities.insert(identity);
        } else {
            // 管理员解绑后不自动重新绑定，避免绕过解绑处置。
            if (!"ACTIVE".equals(identity.getBindStatus())) {
                throw new BusinessException("WECOM_BINDING_INACTIVE", HttpStatus.FORBIDDEN, "企业微信绑定已解除，请联系管理员");
            }
            user = users.selectById(identity.getUserId());
        }
        if (user == null || !Integer.valueOf(0).equals(user.getDeleted())
                || "BANNED".equals(user.getStatus()) || "CANCELLED".equals(user.getStatus())
                || ("SUSPENDED".equals(user.getStatus()) && (user.getSuspendUntil() == null
                    || user.getSuspendUntil().isAfter(LocalDateTime.now())))) {
            throw new BusinessException("ACCOUNT_UNAVAILABLE", HttpStatus.FORBIDDEN, "账号当前不可登录，请联系管理员");
        }
        user.setLastLoginAt(LocalDateTime.now()); users.updateById(user);
        return new AppAuthService.LoginResult(user.getId(), user.getNickname(), user.getStatus());
    }
}
