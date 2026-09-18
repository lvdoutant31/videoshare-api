package com.yingliu.service.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yingliu.common.exception.BusinessException;
import com.yingliu.domain.user.management.AdminConfirmation;
import com.yingliu.domain.user.management.ManagedUser;
import com.yingliu.domain.user.management.UserGovernanceAuditLog;
import com.yingliu.domain.user.management.UserPenalty;
import com.yingliu.domain.user.management.UserWeComIdentity;
import com.yingliu.service.content.shared.ServicePage;
import com.yingliu.service.user.mapper.AdminConfirmationMapper;
import com.yingliu.service.user.mapper.ManagedUserMapper;
import com.yingliu.service.user.mapper.UserGovernanceAuditLogMapper;
import com.yingliu.service.user.mapper.UserPenaltyMapper;
import com.yingliu.service.user.mapper.UserWeComIdentityMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

/** 用户治理事务实现，协调处罚记录、账号状态、确认令牌和审计记录。 */
@Service
public class DefaultAdminUserManagementService implements AdminUserManagementService {
    private static final SecureRandom TOKEN_RANDOM = new SecureRandom();
    private final ManagedUserMapper userMapper;
    private final UserPenaltyMapper penaltyMapper;
    private final UserWeComIdentityMapper weComMapper;
    private final AdminConfirmationMapper confirmationMapper;
    private final UserGovernanceAuditLogMapper auditLogMapper;
    private final JdbcTemplate jdbcTemplate;

    public DefaultAdminUserManagementService(ManagedUserMapper userMapper, UserPenaltyMapper penaltyMapper,
                                             UserWeComIdentityMapper weComMapper, AdminConfirmationMapper confirmationMapper,
                                             UserGovernanceAuditLogMapper auditLogMapper, JdbcTemplate jdbcTemplate) {
        this.userMapper = userMapper;
        this.penaltyMapper = penaltyMapper;
        this.weComMapper = weComMapper;
        this.confirmationMapper = confirmationMapper;
        this.auditLogMapper = auditLogMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public ServicePage<UserSummary> list(UserQuery query) {
        LambdaQueryWrapper<ManagedUser> wrapper = new LambdaQueryWrapper<ManagedUser>()
                .and(query.keyword() != null && !query.keyword().isBlank(), item -> item
                        .like(ManagedUser::getNickname, query.keyword())
                        .or().eq(ManagedUser::getId, numericId(query.keyword())))
                .orderByDesc(ManagedUser::getCreatedAt).orderByDesc(ManagedUser::getId);
        if (query.status() == null || query.status().isBlank()) {
            wrapper.eq(ManagedUser::getDeleted, 0);
        } else {
            if ("CANCELLED".equals(query.status())) {
                wrapper.eq(ManagedUser::getDeleted, 1);
            } else {
                wrapper.eq(ManagedUser::getDeleted, 0).eq(ManagedUser::getStatus, query.status());
            }
        }
        Page<ManagedUser> page = userMapper.selectPage(new Page<>(query.page(), query.pageSize()), wrapper);
        return new ServicePage<>(page.getRecords().stream().map(this::summary).toList(), page.getTotal());
    }

    @Override
    public UserDetail get(long userId) {
        ManagedUser user = requireUser(userId);
        reconcile(user);
        return detail(user);
    }

    @Override
    @Transactional
    public UserDetail mute(TemporaryPenaltyCommand command) { return temporary(command, "MUTE", "USER_MUTE"); }

    @Override
    @Transactional
    public UserDetail suspend(TemporaryPenaltyCommand command) { return temporary(command, "SUSPEND", "USER_SUSPEND"); }

    @Override
    @Transactional
    public Confirmation issueBanConfirmation(long actorAdminId, long userId) {
        requireMutableUser(userId);
        byte[] random = new byte[32];
        TOKEN_RANDOM.nextBytes(random);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        AdminConfirmation confirmation = new AdminConfirmation();
        confirmation.setTokenHash(sha256(token));
        confirmation.setActorAdminId(actorAdminId);
        confirmation.setAction("USER_BAN");
        confirmation.setTargetType("USER");
        confirmation.setTargetId(userId);
        confirmation.setExpiresAt(LocalDateTime.now().plusSeconds(60));
        confirmationMapper.insert(confirmation);
        return new Confirmation(token, confirmation.getExpiresAt());
    }

    @Override
    @Transactional
    public UserDetail ban(BanCommand command) {
        ManagedUser user = requireMutableUser(command.userId());
        reconcile(user);
        String reason = requireReason(command.reason());
        if (hasActivePenalty(user.getId(), "BAN")) throw stateConflict();
        if (confirmationMapper.consume(sha256(command.confirmationToken()), command.actorAdminId(), "USER_BAN", "USER", command.userId(), LocalDateTime.now()) != 1) {
            throw new BusinessException("CONFIRMATION_INVALID", HttpStatus.CONFLICT, "永久封禁确认已失效，请重新确认");
        }
        String before = snapshot(user);
        insertPenalty(user.getId(), command.actorAdminId(), "BAN", reason, null);
        user.setViolationCount(safeInt(user.getViolationCount()) + 1);
        recompute(user);
        audit(command.actorAdminId(), "USER_BAN", user.getId(), reason, before, snapshot(user), command.traceId());
        return detail(user);
    }

    @Override
    @Transactional
    public UserDetail revokePenalty(RevokePenaltyCommand command) {
        ManagedUser user = requireMutableUser(command.userId());
        reconcile(user);
        UserPenalty penalty = penaltyMapper.selectOne(new LambdaQueryWrapper<UserPenalty>().eq(UserPenalty::getId, command.penaltyId()).eq(UserPenalty::getUserId, command.userId()));
        if (penalty == null) throw new BusinessException("PENALTY_NOT_FOUND", HttpStatus.NOT_FOUND, "处罚记录不存在");
        if (!isActive(penalty, LocalDateTime.now())) throw new BusinessException("PENALTY_NOT_ACTIVE", HttpStatus.CONFLICT, "处罚已失效或已撤销");
        String reason = requireReason(command.reason());
        String before = snapshot(user);
        if (penaltyMapper.update(null, new LambdaUpdateWrapper<UserPenalty>().eq(UserPenalty::getId, penalty.getId()).eq(UserPenalty::getStatus, "ACTIVE")
                .set(UserPenalty::getStatus, "REVOKED").set(UserPenalty::getRevokedBy, command.actorAdminId()).set(UserPenalty::getRevokeReason, reason)) != 1) {
            throw new BusinessException("PENALTY_NOT_ACTIVE", HttpStatus.CONFLICT, "处罚已失效或已撤销");
        }
        recompute(user);
        audit(command.actorAdminId(), "USER_PENALTY_REVOKE", user.getId(), reason, before, snapshot(user), command.traceId());
        return detail(user);
    }

    @Override
    @Transactional
    public UserDetail unbindWeCom(UnbindWeComCommand command) {
        ManagedUser user = requireMutableUser(command.userId());
        UserWeComIdentity identity = weComMapper.selectOne(new LambdaQueryWrapper<UserWeComIdentity>().eq(UserWeComIdentity::getUserId, user.getId()).eq(UserWeComIdentity::getBindStatus, "ACTIVE").last("LIMIT 1"));
        if (identity == null) throw new BusinessException("WECOM_NOT_BOUND", HttpStatus.CONFLICT, "用户未绑定企业微信");
        String reason = requireReason(command.reason());
        String before = snapshot(user);
        identity.setBindStatus("UNBOUND");
        identity.setUnboundAt(LocalDateTime.now());
        weComMapper.updateById(identity);
        audit(command.actorAdminId(), "USER_WECOM_UNBIND", user.getId(), reason, before, snapshot(user), command.traceId());
        return detail(user);
    }

    private UserDetail temporary(TemporaryPenaltyCommand command, String type, String action) {
        ManagedUser user = requireMutableUser(command.userId());
        reconcile(user);
        String reason = requireReason(command.reason());
        if (command.endsAt() == null || !command.endsAt().isAfter(LocalDateTime.now())) throw validation("处罚期限必须晚于当前时间");
        if (hasActivePenalty(user.getId(), type) || "BANNED".equals(user.getStatus()) || ("MUTE".equals(type) && "SUSPENDED".equals(user.getStatus()))) throw stateConflict();
        String before = snapshot(user);
        insertPenalty(user.getId(), command.actorAdminId(), type, reason, command.endsAt());
        user.setViolationCount(safeInt(user.getViolationCount()) + 1);
        recompute(user);
        audit(command.actorAdminId(), action, user.getId(), reason, before, snapshot(user), command.traceId());
        return detail(user);
    }

    private ManagedUser requireUser(long userId) {
        ManagedUser user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException("USER_NOT_FOUND", HttpStatus.NOT_FOUND, "用户不存在");
        return user;
    }

    private ManagedUser requireMutableUser(long userId) {
        ManagedUser user = requireUser(userId);
        if (Integer.valueOf(1).equals(user.getDeleted())) throw stateConflict();
        return user;
    }

    private void reconcile(ManagedUser user) {
        if (Integer.valueOf(1).equals(user.getDeleted())) return;
        String expected = effectiveStatus(activePenalties(user.getId()));
        LocalDateTime muteUntil = latestEnd(user.getId(), "MUTE");
        LocalDateTime suspendUntil = latestEnd(user.getId(), "SUSPEND");
        if (!expected.equals(user.getStatus()) || !same(user.getMuteUntil(), muteUntil) || !same(user.getSuspendUntil(), suspendUntil)) {
            user.setStatus(expected); user.setMuteUntil(muteUntil); user.setSuspendUntil(suspendUntil); userMapper.updateById(user);
        }
    }

    private void recompute(ManagedUser user) { reconcile(user); }

    private List<UserPenalty> activePenalties(long userId) {
        LocalDateTime now = LocalDateTime.now();
        return penaltyMapper.selectList(new LambdaQueryWrapper<UserPenalty>().eq(UserPenalty::getUserId, userId).eq(UserPenalty::getStatus, "ACTIVE")
                .and(item -> item.isNull(UserPenalty::getEndsAt).or().gt(UserPenalty::getEndsAt, now)));
    }

    private boolean hasActivePenalty(long userId, String type) { return activePenalties(userId).stream().anyMatch(p -> type.equals(p.getPenaltyType())); }
    private LocalDateTime latestEnd(long userId, String type) { return activePenalties(userId).stream().filter(p -> type.equals(p.getPenaltyType())).map(UserPenalty::getEndsAt).filter(java.util.Objects::nonNull).max(LocalDateTime::compareTo).orElse(null); }
    private String effectiveStatus(List<UserPenalty> penalties) {
        if (penalties.stream().anyMatch(p -> "BAN".equals(p.getPenaltyType()))) return "BANNED";
        if (penalties.stream().anyMatch(p -> "SUSPEND".equals(p.getPenaltyType()))) return "SUSPENDED";
        return penalties.stream().anyMatch(p -> "MUTE".equals(p.getPenaltyType())) ? "MUTED" : "NORMAL";
    }
    private boolean isActive(UserPenalty penalty, LocalDateTime now) { return "ACTIVE".equals(penalty.getStatus()) && (penalty.getEndsAt() == null || penalty.getEndsAt().isAfter(now)); }

    private void insertPenalty(long userId, long actorAdminId, String type, String reason, LocalDateTime endsAt) {
        UserPenalty penalty = new UserPenalty(); penalty.setUserId(userId); penalty.setPenaltyType(type); penalty.setReason(reason);
        penalty.setStartsAt(LocalDateTime.now()); penalty.setEndsAt(endsAt); penalty.setStatus("ACTIVE"); penalty.setIssuedBy(actorAdminId); penaltyMapper.insert(penalty);
    }

    private UserSummary summary(ManagedUser user) { return new UserSummary(user.getId(), user.getNickname(), null, null, Integer.valueOf(1).equals(user.getDeleted()) ? "CANCELLED" : user.getStatus(), safeLong(user.getFollowerCount()), safeLong(user.getFollowingCount()), safeInt(user.getViolationCount()), user.getCreatedAt(), user.getLastLoginAt(), user.getMuteUntil(), user.getSuspendUntil()); }
    private UserDetail detail(ManagedUser user) {
        UserSummary summary = summary(user);
        UserWeComIdentity binding = weComMapper.selectOne(new LambdaQueryWrapper<UserWeComIdentity>().eq(UserWeComIdentity::getUserId, user.getId()).eq(UserWeComIdentity::getBindStatus, "ACTIVE").last("LIMIT 1"));
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(DISTINCT device_hash) FROM sys_login_log WHERE principal_type = 'APP_USER' AND principal_id = ? AND success = 1 AND device_hash IS NOT NULL", Integer.class, user.getId());
        LocalDateTime lastActiveAt = jdbcTemplate.queryForObject("SELECT MAX(created_at) FROM sys_login_log WHERE principal_type = 'APP_USER' AND principal_id = ? AND success = 1", LocalDateTime.class, user.getId());
        List<PenaltyView> penalties = penaltyMapper.selectList(new LambdaQueryWrapper<UserPenalty>().eq(UserPenalty::getUserId, user.getId()).orderByDesc(UserPenalty::getCreatedAt)).stream()
                .map(p -> new PenaltyView(p.getId(), p.getPenaltyType(), p.getReason(), p.getStartsAt(), p.getEndsAt(), isActive(p, LocalDateTime.now()), "REVOKED".equals(p.getStatus()) ? p.getUpdatedAt() : null, p.getRevokeReason(), operator(p.getIssuedBy()))).toList();
        return new UserDetail(summary.id(), summary.nickname(), summary.avatarUrl(), summary.maskedPhone(), summary.status(), summary.followerCount(), summary.followingCount(), summary.violationCount(), summary.createdAt(), summary.lastLoginAt(), summary.muteUntil(), summary.suspendUntil(), new WeComBindingView(binding != null, binding == null ? null : binding.getBoundAt(), binding == null ? null : binding.getDisplayName()), new DeviceSummary(count == null ? 0 : count, lastActiveAt), penalties);
    }

    private String operator(Long id) { if (id == null) return "系统"; String name = jdbcTemplate.query("SELECT username FROM sys_admin_user WHERE id = ?", rs -> rs.next() ? rs.getString(1) : null, id); return name == null ? "管理员" : name; }
    private void audit(long actorId, String action, long userId, String reason, String before, String after, String traceId) { UserGovernanceAuditLog log = new UserGovernanceAuditLog(); log.setActorType("ADMIN"); log.setActorId(actorId); log.setAction(action); log.setTargetType("USER"); log.setTargetId(userId); log.setReason(reason); log.setBeforeSnapshot(before); log.setAfterSnapshot(after); log.setResult("SUCCESS"); log.setTraceId(traceId == null ? null : traceId.substring(0, Math.min(32, traceId.length()))); auditLogMapper.insert(log); }
    private String snapshot(ManagedUser user) {
        return "{\"status\":\"" + user.getStatus() + "\",\"muteUntil\":" + quote(user.getMuteUntil())
                + ",\"suspendUntil\":" + quote(user.getSuspendUntil()) + "}";
    }
    private String quote(Object value) { return value == null ? "null" : "\"" + value + "\""; }
    private String requireReason(String value) { String normalized = value == null ? "" : value.trim(); if (normalized.isEmpty() || normalized.length() > 512) throw validation(normalized.isEmpty() ? "治理原因不能为空" : "治理原因长度超出限制"); return normalized; }
    private BusinessException validation(String message) { return new BusinessException("VALIDATION_FAILED", HttpStatus.UNPROCESSABLE_ENTITY, message); }
    private BusinessException stateConflict() { return new BusinessException("STATE_CONFLICT", HttpStatus.CONFLICT, "用户当前状态不允许执行该操作"); }
    private Long numericId(String value) { try { long id = Long.parseLong(value.trim()); return id > 0 ? id : -1L; } catch (Exception ignored) { return -1L; } }
    private String sha256(String value) { if (value == null || value.isBlank()) return ""; try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); } }
    private long safeLong(Long value) { return value == null ? 0L : value; }
    private int safeInt(Integer value) { return value == null ? 0 : value; }
    private boolean same(Object left, Object right) { return java.util.Objects.equals(left, right); }
}
