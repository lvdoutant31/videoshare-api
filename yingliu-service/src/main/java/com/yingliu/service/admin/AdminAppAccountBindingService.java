package com.yingliu.service.admin;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.yingliu.common.exception.BusinessException;
import com.yingliu.common.security.PhoneCrypto;
import com.yingliu.domain.content.video.AdminUser;
import com.yingliu.domain.user.AppUser;
import com.yingliu.service.app.mapper.AppUserMapper;
import com.yingliu.service.content.video.mapper.AdminUserMapper;
import com.yingliu.service.content.video.mapper.VideoAuthorMapper;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

/** Links the administrator's existing author profile to one C-side password account. */
@Service
public class AdminAppAccountBindingService {
    private final AdminUserMapper admins;
    private final VideoAuthorMapper authors;
    private final AppUserMapper users;
    private final String phoneCryptoKey;

    public AdminAppAccountBindingService(AdminUserMapper admins, VideoAuthorMapper authors, AppUserMapper users,
            @Value("${yingliu.app.phone-crypto-key:dev-phone-crypto-key-0000}") String phoneCryptoKey) {
        this.admins = admins;
        this.authors = authors;
        this.users = users;
        this.phoneCryptoKey = phoneCryptoKey;
    }

    @Transactional
    public BindingView bind(long adminId, String phone, String password) {
        validate(phone, password);
        AdminUser admin = requireActiveAdmin(adminId);
        AppUser author = author(admin);
        if (author.getPhoneHmac() != null || author.getPasswordHash() != null) {
            throw conflict("该管理员关联账号已设置登录凭据");
        }
        String phoneHmac = PhoneCrypto.hmac(phone, phoneCryptoKey);
        AppUser owner = users.selectAnyByPhoneHmac(phoneHmac);
        if (owner != null && !owner.getId().equals(author.getId())) {
            throw conflict("该手机号已关联其他账号，不能自动合并");
        }
        author.setPhoneCiphertext(PhoneCrypto.encrypt(phone, phoneCryptoKey));
        author.setPhoneHmac(phoneHmac);
        author.setPasswordHash(BCrypt.hashpw(password, BCrypt.gensalt()));
        try {
            if (users.updateById(author) != 1) throw conflict("管理员手机号绑定失败");
        } catch (DuplicateKeyException exception) {
            throw conflict("该手机号已关联其他账号，不能自动合并");
        }
        return new BindingView(true, mask(phone));
    }

    public BindingView get(long adminId) {
        AdminUser admin = requireActiveAdmin(adminId);
        if (admin.getUserId() == null) return new BindingView(false, null);
        AppUser author = users.selectById(admin.getUserId());
        if (author == null || author.getPhoneCiphertext() == null || author.getPhoneHmac() == null) {
            return new BindingView(false, null);
        }
        return new BindingView(true, mask(PhoneCrypto.decrypt(author.getPhoneCiphertext(), phoneCryptoKey)));
    }

    private AdminUser requireActiveAdmin(long adminId) {
        AdminUser admin = admins.selectForAuthorBinding(adminId);
        if (admin == null || !"NORMAL".equals(admin.getStatus())) {
            throw new BusinessException("ADMIN_NOT_ELIGIBLE", HttpStatus.FORBIDDEN, "后台账号不可用");
        }
        return admin;
    }

    private AppUser author(AdminUser admin) {
        if (admin.getUserId() != null) {
            AppUser author = users.selectById(admin.getUserId());
            if (author == null || !Integer.valueOf(0).equals(author.getDeleted())) {
                throw conflict("管理员作者账号不可用");
            }
            return author;
        }
        long authorId = IdWorker.getId();
        if (authors.insertAdminAuthor(authorId, admin.getUsername()) != 1 || admins.bindAuthor(admin.getId(), authorId) != 1) {
            throw conflict("管理员作者账号创建失败");
        }
        return users.selectById(authorId);
    }

    private void validate(String phone, String password) {
        if (phone == null || !phone.matches("^1\\d{10}$")) {
            throw new BusinessException("VALIDATION_FAILED", HttpStatus.UNPROCESSABLE_ENTITY, "手机号格式不正确");
        }
        int bytes = password == null ? 0 : password.getBytes(StandardCharsets.UTF_8).length;
        if (password == null || password.length() < 8 || password.length() > 64 || bytes > 72) {
            throw new BusinessException("VALIDATION_FAILED", HttpStatus.UNPROCESSABLE_ENTITY,
                    "登录密码须为 8 至 64 个字符，且编码后不超过 72 字节");
        }
    }

    private String mask(String phone) {
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    private BusinessException conflict(String message) {
        return new BusinessException("STATE_CONFLICT", HttpStatus.CONFLICT, message);
    }

    public record BindingView(boolean bound, String maskedPhone) { }
}
