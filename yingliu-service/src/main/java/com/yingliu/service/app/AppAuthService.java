package com.yingliu.service.app;

import com.yingliu.service.app.support.AppUserView;
import com.yingliu.service.content.shared.CursorPage;

import java.util.List;

/**
 * App 域登录与账号（API §2.1）。
 * 手机号验证码登录/注册、资料、企微绑定状态、协议、注销申请。
 */
public interface AppAuthService {

    /** 手机号密码注册；手机号必须尚未注册。 */
    LoginResult registerByPassword(String phone, String password, String name, String agreementVersion);

    /** 手机号密码登录。 */
    LoginResult loginByPassword(String phone, String password);

    /** 发送手机号验证码；返回有效期秒数。开发模式（sms.send-enabled=false）固定 123456 */
    int sendPhoneCode(String phone, String clientIp);

    /** 手机号验证码登录/注册；校验协议版本并记录同意 */
    LoginResult loginByPhone(String phone, String code, String agreementVersion);

    AppUserView getMe(long userId);

    AppUserView updateMe(long userId, String nickname, String bio);

    /** 已同意协议版本列表 */
    List<AgreementView> getAgreements(long userId);

    /** 同意最新版本协议 */
    void acceptAgreement(long userId, String type);

    /** 发起账号注销申请（匿名化公开资料并置 CANCELLED） */
    void requestCancellation(long userId);

    /** 生成企微 OAuth 跳转地址与一次性 state；企微未配置时抛 WECOM_UNAVAILABLE */
    WecomAuthorizeResult getWecomAuthorizeUrl();

    /** 消费一次性 state、交换企业成员身份并返回平台账号；会话由 API 层签发。 */
    LoginResult wecomCallback(String code, String state);

    record LoginResult(long userId, String nickname, String status) { }

    record AgreementView(String type, String version, String agreedAt, String currentVersion) { }

    record WecomAuthorizeResult(String authorizeUrl, String state) { }
}
