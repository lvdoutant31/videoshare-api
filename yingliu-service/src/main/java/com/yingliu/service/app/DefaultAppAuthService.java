package com.yingliu.service.app;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yingliu.common.exception.BusinessException;
import com.yingliu.common.security.PhoneCrypto;
import com.yingliu.domain.content.video.MediaResource;
import com.yingliu.domain.user.AppUser;
import com.yingliu.domain.user.SysUserAgreement;
import com.yingliu.domain.user.SysVerificationRecord;
import com.yingliu.media.storage.MediaStorage;
import com.yingliu.service.app.mapper.AppUserMapper;
import com.yingliu.service.app.mapper.SysUserAgreementMapper;
import com.yingliu.service.app.mapper.SysVerificationRecordMapper;
import com.yingliu.service.app.support.AppUserView;
import com.yingliu.service.app.support.VerificationCodeStore;
import com.yingliu.service.content.video.mapper.MediaResourceMapper;
import com.yingliu.service.user.mapper.UserWeComIdentityMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@Profile("!test")
public class DefaultAppAuthService implements AppAuthService {
    private static final Logger log = LoggerFactory.getLogger(DefaultAppAuthService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String CODE_KEY_PREFIX = "yingliu:sms:code:";
    private static final String LIMIT_KEY_PREFIX = "yingliu:sms:limit:";
    private static final int CODE_TTL_SECONDS = 300;
    private static final String AGREEMENT_CURRENT_VERSION = "2026-08";

    private final AppUserMapper userMapper;
    private final SysVerificationRecordMapper verificationMapper;
    private final SysUserAgreementMapper agreementMapper;
    private final MediaResourceMapper mediaMapper;
    private final MediaStorage mediaStorage;
    private final UserWeComIdentityMapper wecomMapper;
    private final VerificationCodeStore codeStore;
    private final String cryptoKey;
    private final boolean smsSendEnabled;
    private final int smsPerMinuteLimit;
    private final WecomLoginService wecomLogin;

    public DefaultAppAuthService(AppUserMapper userMapper,
                                 SysVerificationRecordMapper verificationMapper,
                                 SysUserAgreementMapper agreementMapper,
                                 MediaResourceMapper mediaMapper,
                                 MediaStorage mediaStorage,
                                 UserWeComIdentityMapper wecomMapper,
                                 VerificationCodeStore codeStore,
                                 @Value("${yingliu.app.phone-crypto-key:dev-phone-crypto-key-0000}") String cryptoKey,
                                 @Value("${yingliu.app.sms-send-enabled:false}") boolean smsSendEnabled,
                                 @Value("${yingliu.app.sms-per-minute-limit:5}") int smsPerMinuteLimit,
                                 WecomLoginService wecomLogin) {
        this.userMapper = userMapper;
        this.verificationMapper = verificationMapper;
        this.agreementMapper = agreementMapper;
        this.mediaMapper = mediaMapper;
        this.mediaStorage = mediaStorage;
        this.wecomMapper = wecomMapper;
        this.codeStore = codeStore;
        this.cryptoKey = cryptoKey;
        this.smsSendEnabled = smsSendEnabled;
        this.smsPerMinuteLimit = Math.max(1, smsPerMinuteLimit);
        this.wecomLogin = wecomLogin;
    }

    @Override
    @Transactional
    public LoginResult registerByPassword(String phone, String password, String name, String agreementVersion) {
        validatePassword(password);
        String normalizedName = validateName(name);
        String hmac = PhoneCrypto.hmac(phone, cryptoKey);
        if (userMapper.selectByPhoneHmac(hmac) != null) {
            throw new BusinessException("STATE_CONFLICT", HttpStatus.CONFLICT, "该手机号已注册");
        }
        AppUser user = createUser(phone, hmac, BCrypt.hashpw(password, BCrypt.gensalt()), normalizedName);
        if (agreementVersion != null && !agreementVersion.isBlank()) {
            recordAgreement(user.getId(), "USER_AGREEMENT", agreementVersion);
        }
        return new LoginResult(user.getId(), user.getNickname(), user.getStatus());
    }

    @Override
    @Transactional
    public LoginResult loginByPassword(String phone, String password) {
        validatePassword(password);
        AppUser user = userMapper.selectByPhoneHmac(PhoneCrypto.hmac(phone, cryptoKey));
        if (user == null || user.getPasswordHash() == null || !BCrypt.checkpw(password, user.getPasswordHash())) {
            throw new BusinessException("VALIDATION_FAILED", HttpStatus.UNAUTHORIZED, "手机号或密码错误");
        }
        assertAccountUsable(user);
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);
        return new LoginResult(user.getId(), user.getNickname(), user.getStatus());
    }

    @Override
    public int sendPhoneCode(String phone, String clientIp) {
        if (!phone.matches("^1\\d{10}$")) {
            throw new BusinessException("VALIDATION_FAILED", HttpStatus.UNPROCESSABLE_ENTITY, "手机号格式不正确");
        }
        // 按 IP + 手机号摘要限流：每分钟不超过 5 次
        String hmac = PhoneCrypto.hmac(phone, cryptoKey);
        String limitKey = LIMIT_KEY_PREFIX + clientIp + ":" + System.currentTimeMillis() / 60_000;
        long sent = codeStore.increment(limitKey, 60);
        if (sent > smsPerMinuteLimit) {
            throw new BusinessException("RATE_LIMITED", HttpStatus.TOO_MANY_REQUESTS, "发送过于频繁，请稍后再试");
        }
        String code = smsSendEnabled ? String.format("%06d", RANDOM.nextInt(1_000_000)) : "123456";
        codeStore.set(CODE_KEY_PREFIX + "LOGIN:" + hmac, code, CODE_TTL_SECONDS);

        SysVerificationRecord record = new SysVerificationRecord();
        record.setTargetHmac(hmac);
        record.setPurpose("LOGIN");
        record.setStatus("SENT");
        record.setExpiresAt(LocalDateTime.now().plusSeconds(CODE_TTL_SECONDS));
        verificationMapper.insert(record);

        if (smsSendEnabled) {
            // 首期不接真实短信服务商，仅日志输出；接入后在此调用发送服务
            log.debug("开发环境模拟验证码 phone={} code={}", mask(phone), code);
        }
        return CODE_TTL_SECONDS;
    }

    @Override
    @Transactional
    public LoginResult loginByPhone(String phone, String code, String agreementVersion) {
        String hmac = PhoneCrypto.hmac(phone, cryptoKey);
        String redisKey = CODE_KEY_PREFIX + "LOGIN:" + hmac;
        String stored = codeStore.get(redisKey);
        if (stored == null || !stored.equals(code)) {
            throw new BusinessException("VALIDATION_FAILED", HttpStatus.UNPROCESSABLE_ENTITY, "验证码错误或已过期");
        }
        codeStore.delete(redisKey);

        AppUser user = userMapper.selectByPhoneHmac(hmac);
        if (user == null) {
            user = createUser(phone, hmac);
        }
        assertAccountUsable(user);
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

        if (agreementVersion != null && !agreementVersion.isBlank()) {
            recordAgreement(user.getId(), "USER_AGREEMENT", agreementVersion);
        }
        return new LoginResult(user.getId(), user.getNickname(), user.getStatus());
    }

    @Override
    public AppUserView getMe(long userId) {
        AppUser user = requireUser(userId);
        return toView(user);
    }

    @Override
    @Transactional
    public AppUserView updateMe(long userId, String nickname, String bio) {
        if (nickname == null || nickname.isBlank() || nickname.length() > 30) {
            throw new BusinessException("VALIDATION_FAILED", HttpStatus.UNPROCESSABLE_ENTITY, "昵称需为 1-30 个字符");
        }
        if (bio != null && bio.length() > 100) {
            throw new BusinessException("VALIDATION_FAILED", HttpStatus.UNPROCESSABLE_ENTITY, "简介不能超过 100 个字符");
        }
        AppUser user = requireUser(userId);
        user.setNickname(nickname.trim());
        user.setBio(bio == null ? null : bio.trim());
        userMapper.updateById(user);
        return toView(user);
    }

    @Override
    public List<AgreementView> getAgreements(long userId) {
        return List.of(
                agreementView(userId, "USER_AGREEMENT"),
                agreementView(userId, "PRIVACY_POLICY"));
    }

    @Override
    @Transactional
    public void acceptAgreement(long userId, String type) {
        if (!List.of("USER_AGREEMENT", "PRIVACY_POLICY").contains(type)) {
            throw new BusinessException("VALIDATION_FAILED", HttpStatus.UNPROCESSABLE_ENTITY, "协议类型不合法");
        }
        recordAgreement(userId, type, AGREEMENT_CURRENT_VERSION);
    }

    @Override
    @Transactional
    public void requestCancellation(long userId) {
        AppUser user = requireUser(userId);
        user.setStatus("CANCELLED");
        user.setNickname("用户" + String.valueOf(userId).substring(Math.max(0, String.valueOf(userId).length() - 4)));
        user.setBio(null);
        userMapper.updateById(user);
    }

    @Override
    public WecomAuthorizeResult getWecomAuthorizeUrl() {
        return wecomLogin.authorize();
    }

    @Override
    public LoginResult wecomCallback(String code, String state) {
        return wecomLogin.login(code, state);
    }

    private AppUser createUser(String phone, String hmac) {
        return createUser(phone, hmac, null, null);
    }

    private AppUser createUser(String phone, String hmac, String passwordHash, String name) {
        AppUser user = new AppUser();
        user.setPhoneCiphertext(PhoneCrypto.encrypt(phone, cryptoKey));
        user.setPhoneHmac(hmac);
        user.setPasswordHash(passwordHash);
        user.setNickname(name == null ? "用户" + phone.substring(phone.length() - 4) : name);
        user.setStatus("NORMAL");
        user.setViolationCount(0);
        user.setLikeReceivedCount(0L);
        user.setFollowingCount(0L);
        user.setFollowerCount(0L);
        user.setDeleted(0);
        userMapper.insert(user);
        return user;
    }

    private void validatePassword(String password) {
        int bytes = password == null ? 0 : password.getBytes(StandardCharsets.UTF_8).length;
        if (password == null || password.length() < 8 || password.length() > 64 || bytes > 72) {
            throw new BusinessException("VALIDATION_FAILED", HttpStatus.UNPROCESSABLE_ENTITY,
                    "密码须为 8 至 64 个字符，且编码后不超过 72 字节");
        }
    }

    private String validateName(String name) {
        String normalized = name == null ? "" : name.trim();
        if (normalized.isEmpty() || normalized.length() > 30) {
            throw new BusinessException("VALIDATION_FAILED", HttpStatus.UNPROCESSABLE_ENTITY,
                    "姓名须为 1 至 30 个字符");
        }
        return normalized;
    }

    private void assertAccountUsable(AppUser user) {
        if ("BANNED".equals(user.getStatus())) {
            throw new BusinessException("ACCOUNT_SUSPENDED", HttpStatus.FORBIDDEN, "账号已被永久封禁");
        }
        if ("SUSPENDED".equals(user.getStatus())
                && (user.getSuspendUntil() == null || user.getSuspendUntil().isAfter(LocalDateTime.now()))) {
            throw new BusinessException("ACCOUNT_SUSPENDED", HttpStatus.FORBIDDEN, "账号暂时封禁中，请联系管理员");
        }
        if ("CANCELLED".equals(user.getStatus())) {
            throw new BusinessException("ACCOUNT_SUSPENDED", HttpStatus.FORBIDDEN, "账号已注销");
        }
    }

    private AppUser requireUser(long userId) {
        AppUser user = userMapper.selectById(userId);
        if (user == null || Objects.equals(user.getDeleted(), 1)) {
            throw new BusinessException("RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    private void recordAgreement(long userId, String type, String version) {
        SysUserAgreement existing = agreementMapper.selectLatest(userId, type);
        if (existing != null && existing.getVersion().equals(version)) {
            return;
        }
        SysUserAgreement agreement = new SysUserAgreement();
        agreement.setUserId(userId);
        agreement.setAgreementType(type);
        agreement.setVersion(version);
        agreement.setAgreedAt(LocalDateTime.now());
        agreementMapper.insert(agreement);
    }

    private AgreementView agreementView(long userId, String type) {
        SysUserAgreement latest = agreementMapper.selectLatest(userId, type);
        return new AgreementView(type,
                latest == null ? null : latest.getVersion(),
                latest == null ? null : String.valueOf(latest.getAgreedAt()),
                AGREEMENT_CURRENT_VERSION);
    }

    private AppUserView toView(AppUser user) {
        String avatarUrl = null;
        if (user.getAvatarMediaId() != null) {
            MediaResource media = mediaMapper.selectById(user.getAvatarMediaId());
            if (media != null && media.getRelativePath() != null) {
                avatarUrl = mediaStorage.getUrl(media.getRelativePath());
            }
        }
        boolean wecomBound = wecomMapper.countActiveBindings(user.getId()) > 0;
        return new AppUserView(String.valueOf(user.getId()), user.getNickname(), avatarUrl, user.getBio(), user.getStatus(),
                user.getLikeReceivedCount() == null ? 0 : user.getLikeReceivedCount(),
                user.getFollowingCount() == null ? 0 : user.getFollowingCount(),
                user.getFollowerCount() == null ? 0 : user.getFollowerCount(),
                wecomBound);
    }

    private static String mask(String phone) {
        return phone.length() >= 7 ? phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4) : "****";
    }
}
