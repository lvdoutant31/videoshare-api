package com.yingliu.api.admin.auth;

import org.mindrot.jbcrypt.BCrypt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Profile("local")
public class LocalUserManagementSeed implements CommandLineRunner {
    private static final long ADMIN_ID = 9000001L;
    private final JdbcTemplate jdbcTemplate;
    private final boolean enabled;

    public LocalUserManagementSeed(JdbcTemplate jdbcTemplate, @Value("${yingliu.local.seed.enabled:false}") boolean enabled) {
        this.jdbcTemplate = jdbcTemplate;
        this.enabled = enabled;
    }

    @Override
    public void run(String... args) {
        if (!enabled) return;
        LocalDateTime now = LocalDateTime.now();
        insertAdmin(now);
        jdbcTemplate.update("INSERT IGNORE INTO sys_admin_user_role (admin_user_id, role_id, created_at) VALUES (?, 1, ?)", ADMIN_ID, now);
        insertUser(9000101L, "本地正常用户", "NORMAL", 0, now);
        insertUser(9000102L, "本地企业微信用户", "NORMAL", 0, now);
        insertUser(9000103L, "本地已注销用户", "NORMAL", 1, now);
        insertUser(9000104L, "本地禁言用户", "MUTED", 0, now);
        insertUser(9000105L, "本地临时封禁用户", "SUSPENDED", 0, now);
        insertUser(9000106L, "本地永久封禁用户", "BANNED", 0, now);
        insertPenalty(9000401L, 9000104L, "MUTE", "本地联调禁言样本", now.plusDays(3), now);
        insertPenalty(9000402L, 9000105L, "SUSPEND", "本地联调临时封禁样本", now.plusDays(3), now);
        insertPenalty(9000403L, 9000106L, "BAN", "本地联调永久封禁样本", null, now);
        jdbcTemplate.update("""
                INSERT IGNORE INTO sys_user_wecom_identity (id, user_id, corp_id, wecom_user_id_ciphertext, wecom_user_id_hmac, display_name, bind_status, bound_at, created_at, updated_at)
                VALUES (9000201, 9000102, 'local-corp', X'00', 'local-wecom-hash', '本地企业微信', 'ACTIVE', ?, ?, ?)
                """, now, now, now);
        jdbcTemplate.update("""
                INSERT IGNORE INTO sys_login_log (id, principal_type, principal_id, login_method, success, device_hash, created_at)
                VALUES (9000301, 'APP_USER', 9000102, 'PASSWORD', 1, 'local-device-a', ?),
                       (9000302, 'APP_USER', 9000102, 'PASSWORD', 1, 'local-device-b', ?)
                """, now.minusDays(1), now);
    }

    private void insertAdmin(LocalDateTime now) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_admin_user WHERE id = ?", Integer.class, ADMIN_ID);
        if (count != null && count > 0) return;
        jdbcTemplate.update("INSERT INTO sys_admin_user (id, username, password_hash, status, created_at, updated_at) VALUES (?, ?, ?, 'NORMAL', ?, ?)", ADMIN_ID, "local-admin", BCrypt.hashpw("local-admin-password", BCrypt.gensalt()), now, now);
    }

    private void insertPenalty(long id, long userId, String type, String reason, LocalDateTime endsAt, LocalDateTime now) {
        jdbcTemplate.update("""
                INSERT IGNORE INTO user_penalty (id, user_id, penalty_type, reason, starts_at, ends_at, status, issued_by, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?)
                """, id, userId, type, reason, now.minusHours(1), endsAt, ADMIN_ID, now, now);
    }

    private void insertUser(long id, String nickname, String status, int deleted, LocalDateTime now) {
        jdbcTemplate.update("""
                INSERT IGNORE INTO sys_user (id, nickname, status, violation_count, following_count, follower_count, deleted, created_at, updated_at)
                VALUES (?, ?, ?, 0, 0, 0, ?, ?, ?)
                """, id, nickname, status, deleted, now, now);
    }
}
