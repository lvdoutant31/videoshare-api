package com.yingliu.api.app.auth;

import com.yingliu.service.app.support.AppUserView;

/** App 域登录与账号 DTO（§2.1） */
public final class AppAuthDtos {
    private AppAuthDtos() { }

    public record SendCodeRequest(String phone) { }
    public record SendCodeResponse(int expiresIn) { }

    public record PhoneLoginRequest(String phone, String code, String agreementVersion) { }
    public record PhoneLoginResponse(String accessToken, long expiresIn, AppUserView user) { }

    public record UpdateMeRequest(String nickname, String bio) { }

    public record AgreementResponse(String type, String version, String agreedAt, String currentVersion) { }
}
