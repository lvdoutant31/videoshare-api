package com.yingliu.service.moderation;

import com.yingliu.common.exception.BusinessException;
import com.yingliu.service.content.shared.ServicePage;
import org.springframework.http.HttpStatus;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class JdbcModerationService implements ModerationService {
    private final JdbcTemplate db;
    // Older releases stored claimed and rejected reports using different status values.
    private static final String REPORT_STATUS = "CASE WHEN r.status='CLAIMED' THEN 'PROCESSING' WHEN r.status='RESOLVED' AND r.resolution='REJECTED' THEN 'REJECTED' ELSE r.status END";
    private static final String REPORT_FROM = """
             FROM content_report r
             LEFT JOIN sys_user reporter ON reporter.id=r.reporter_id
             LEFT JOIN sys_admin_user assignee ON assignee.id=r.assignee_admin_id
             LEFT JOIN sys_report_reason reason ON reason.reason_code=r.reason_code
             LEFT JOIN video v ON r.target_type='VIDEO' AND v.id=r.target_id
             LEFT JOIN video_comment c ON r.target_type='COMMENT' AND c.id=r.target_id
             LEFT JOIN sys_user target_user ON r.target_type='USER' AND target_user.id=r.target_id
            """;
    private static final String REPORT_SELECT = "SELECT r.id,r.reporter_id,r.target_type,r.target_id,r.reason_code,r.description,"
            + REPORT_STATUS + " AS status,r.risk_level,r.assignee_admin_id,r.resolution,r.resolution_note,r.resolved_at,r.created_at,r.updated_at,"
            + "COALESCE(CASE r.target_type WHEN 'VIDEO' THEN v.title WHEN 'COMMENT' THEN c.content WHEN 'USER' THEN target_user.nickname END,CONCAT(r.target_type,' #',r.target_id)),"
            + "COALESCE(reporter.nickname,CONCAT('用户 #',r.reporter_id)),COALESCE(reason.reason_name,r.reason_code),assignee.username,r.target_snapshot";

    public JdbcModerationService(JdbcTemplate db) { this.db = db; }

    @Override
    public ServicePage<Comment> comments(long page, long size, String status, Long videoId, String keyword) {
        String where = " WHERE 1=1";
        List<Object> args = new ArrayList<>();
        if (status != null) { where += " AND c.status=?"; args.add(status); }
        if (videoId != null) { where += " AND c.video_id=?"; args.add(videoId); }
        if (keyword != null && !keyword.isBlank()) { where += " AND c.content LIKE ?"; args.add("%" + keyword + "%"); }
        String from = " FROM video_comment c JOIN video v ON v.id=c.video_id JOIN sys_user u ON u.id=c.user_id";
        long total = db.queryForObject("SELECT COUNT(*)" + from + where, Long.class, args.toArray());
        args.add((page - 1) * size); args.add(size);
        return new ServicePage<>(db.query("SELECT c.id,c.video_id,v.title,c.user_id,u.nickname,c.content,c.status,c.risk_level,c.like_count,c.reply_count,c.created_at,c.updated_at" + from + where + " ORDER BY c.created_at DESC,c.id DESC LIMIT ?,?", this::commentRow, args.toArray()), total);
    }

    @Override
    public Comment comment(long id) {
        return db.queryForObject("SELECT c.id,c.video_id,v.title,c.user_id,u.nickname,c.content,c.status,c.risk_level,c.like_count,c.reply_count,c.created_at,c.updated_at,c.delete_reason,c.reply_to_comment_id FROM video_comment c JOIN video v ON v.id=c.video_id JOIN sys_user u ON u.id=c.user_id WHERE c.id=?", (row, ignored) -> commentDetailRow(row, id), id);
    }

    private Comment commentRow(java.sql.ResultSet row, int ignored) throws java.sql.SQLException {
        return new Comment(row.getLong(1), row.getLong(2), row.getString(3), row.getLong(4), row.getString(5), row.getString(6), row.getString(7), row.getString(8), row.getLong(9), row.getLong(10), row.getTimestamp(11).toLocalDateTime(), row.getTimestamp(12).toLocalDateTime(), null, null, List.of(), List.of());
    }

    private Comment commentDetailRow(java.sql.ResultSet row, long id) throws java.sql.SQLException {
        LocalDateTime createdAt = row.getTimestamp(11).toLocalDateTime();
        String author = row.getString(5);
        return new Comment(row.getLong(1), row.getLong(2), row.getString(3), row.getLong(4), author, row.getString(6), row.getString(7), row.getString(8), row.getLong(9), row.getLong(10), createdAt, row.getTimestamp(12).toLocalDateTime(), row.getString(13), nullableLong(row, 14), replies(id), history(id, author, createdAt));
    }

    @Override
    @Transactional
    public Comment hideComment(long id, long actor, String reason) {
        int changed = db.update("UPDATE video_comment SET status='HIDDEN',deleted_by=?,delete_reason=?,deleted_at=NOW(3),updated_at=NOW(3) WHERE id=? AND status<>'DELETED'", actor, reason, id);
        if (changed == 0) throw new BusinessException("STATE_CONFLICT", HttpStatus.CONFLICT, "评论状态已变化，不可隐藏");
        audit(actor, "COMMENT_HIDE", id, reason);
        return comment(id);
    }

    @Override
    @Transactional
    public Comment restoreComment(long id, long actor) {
        Long videoId = db.queryForObject("SELECT video_id FROM video_comment WHERE id=?", Long.class, id);
        if (videoId == null) throw new BusinessException("RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND, "评论不存在");
        Boolean videoDeleted = db.queryForObject("SELECT deleted=1 FROM video WHERE id=? FOR UPDATE", Boolean.class, videoId);
        if (Boolean.TRUE.equals(videoDeleted)) throw new BusinessException("STATE_CONFLICT", HttpStatus.CONFLICT, "视频已删除，评论不可恢复");
        int updated = db.update("UPDATE video_comment SET status='PENDING',deleted_by=NULL,delete_reason=NULL,deleted_at=NULL,updated_at=NOW(3) WHERE id=? AND status='HIDDEN' AND video_id=?", id, videoId);
        if (updated == 0) throw new BusinessException("STATE_CONFLICT", HttpStatus.CONFLICT, "评论状态已变化，不可恢复");
        audit(actor, "COMMENT_RESTORE", id, null);
        return comment(id);
    }

    @Override
    @Transactional
    public Comment deleteComment(long id, long actor, String reason) {
        int changed = db.update("UPDATE video_comment SET status='DELETED',deleted_by=?,delete_reason=?,deleted_at=NOW(3),updated_at=NOW(3) WHERE id=?", actor, reason, id);
        if (changed == 0) throw new BusinessException("RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND, "评论不存在");
        audit(actor, "COMMENT_DELETE", id, reason);
        return comment(id);
    }

    @Override
    @Transactional
    public void deleteComments(List<Long> ids, long actor, String reason) { ids.forEach(id -> deleteComment(id, actor, reason)); }

    @Override
    public ServicePage<Report> reports(long page, long size, String status, String targetType, String riskLevel) {
        String where = " WHERE 1=1"; List<Object> args = new ArrayList<>();
        if (status != null) { where += " AND " + REPORT_STATUS + "=?"; args.add("CLAIMED".equals(status) ? "PROCESSING" : status); }
        if (targetType != null) { where += " AND r.target_type=?"; args.add(targetType); }
        if (riskLevel != null) { where += " AND r.risk_level=?"; args.add(riskLevel); }
        long total = db.queryForObject("SELECT COUNT(*) FROM content_report r" + where, Long.class, args.toArray());
        args.add((page - 1) * size); args.add(size);
        return new ServicePage<>(db.query(REPORT_SELECT + REPORT_FROM + where + " ORDER BY r.created_at DESC,r.id DESC LIMIT ?,?", (row, ignored) -> reportRow(row, false), args.toArray()), total);
    }

    @Override
    public Report report(long id) {
        try {
            return db.queryForObject(REPORT_SELECT + REPORT_FROM + " WHERE r.id=?", (row, ignored) -> reportRow(row, true), id);
        } catch (EmptyResultDataAccessException exception) {
            throw new BusinessException("REPORT_NOT_FOUND", HttpStatus.NOT_FOUND, "举报不存在");
        }
    }

    private Report reportRow(java.sql.ResultSet row, boolean detail) throws java.sql.SQLException {
        long id = row.getLong(1);
        LocalDateTime createdAt = row.getTimestamp(13).toLocalDateTime();
        String snapshot = row.getString(19);
        if (snapshot == null || snapshot.isBlank()) snapshot = row.getString(15) + (row.getString(6) == null ? "" : "\n举报说明：" + row.getString(6));
        return new Report(id, row.getLong(2), row.getString(3), row.getLong(4), row.getString(5), row.getString(6), row.getString(7), row.getString(8),
                nullableLong(row, 9), row.getString(10), row.getString(11), row.getTimestamp(12) == null ? null : row.getTimestamp(12).toLocalDateTime(),
                createdAt, row.getTimestamp(14).toLocalDateTime(), row.getString(15), row.getString(16), row.getString(17), row.getString(18), snapshot,
                detail ? history(id, row.getString(16), createdAt, "REPORT") : List.of());
    }

    @Override
    @Transactional
    public void claimReport(long id, long actor) {
        int changed = db.update("UPDATE content_report SET status='CLAIMED',assignee_admin_id=?,updated_at=NOW(3) WHERE id=? AND status='PENDING'", actor, id);
        if (changed != 1) throw new BusinessException("REPORT_ALREADY_CLAIMED", HttpStatus.CONFLICT, "举报已被领取或已处理");
        audit(actor, "REPORT_CLAIM", "REPORT", id, null);
    }

    @Override
    @Transactional
    public void resolveReport(long id, long actor, String resolution, String note) {
        finishReport(id, actor, "RESOLVED", resolution, note);
    }

    @Override
    @Transactional
    public void rejectReport(long id, long actor, String note) { finishReport(id, actor, "REJECTED", "REJECTED", note); }

    private void finishReport(long id, long actor, String status, String resolution, String note) {
        if (note == null || note.isBlank() || note.length() > 300) throw new BusinessException("VALIDATION_FAILED", HttpStatus.UNPROCESSABLE_ENTITY, "请填写有效的处理原因");
        int changed = db.update("UPDATE content_report SET status=?,resolution=?,resolution_note=?,resolved_at=NOW(3),updated_at=NOW(3) WHERE id=? AND status IN ('CLAIMED','PROCESSING') AND assignee_admin_id=?", status, resolution, note.trim(), id, actor);
        if (changed != 1) throw new BusinessException("REPORT_NOT_CLAIMED_BY_CURRENT_OPERATOR", HttpStatus.CONFLICT, "举报状态已变化或不属于当前处理人");
        audit(actor, "REJECTED".equals(status) ? "REPORT_REJECT" : "REPORT_RESOLVE", "REPORT", id, note.trim());
    }

    private List<CommentReply> replies(long id) { return db.query("SELECT c.id,u.nickname,c.content,c.created_at FROM video_comment c JOIN sys_user u ON u.id=c.user_id WHERE c.reply_to_comment_id=? ORDER BY c.created_at,c.id", (row, ignored) -> new CommentReply(row.getLong(1), row.getString(2), row.getString(3), row.getTimestamp(4).toLocalDateTime()), id); }

    private List<ModerationHistory> history(long id, String author, LocalDateTime createdAt) { return history(id, author, createdAt, "COMMENT"); }

    private List<ModerationHistory> history(long id, String author, LocalDateTime createdAt, String targetType) {
        List<ModerationHistory> entries = new ArrayList<>();
        entries.add(new ModerationHistory(targetType + "_CREATED", author, createdAt, null));
        entries.addAll(db.query("SELECT l.action,COALESCE(a.username,CONCAT('管理员 #',l.actor_id)),l.created_at,l.reason FROM sys_audit_log l LEFT JOIN sys_admin_user a ON a.id=l.actor_id WHERE l.target_type=? AND l.target_id=? ORDER BY l.created_at,l.id", (row, ignored) -> new ModerationHistory(row.getString(1), row.getString(2), row.getTimestamp(3).toLocalDateTime(), row.getString(4)), targetType, id));
        return entries;
    }

    private void audit(long actor, String action, long id, String reason) { audit(actor, action, "COMMENT", id, reason); }
    private void audit(long actor, String action, String targetType, long id, String reason) { db.update("INSERT INTO sys_audit_log (id,actor_type,actor_id,action,target_type,target_id,reason,result,created_at) VALUES (?, 'ADMIN', ?, ?, ?, ?, ?, 'SUCCESS', NOW(3))", Math.abs(System.nanoTime()), actor, action, targetType, id, reason); }

    private static Long nullableLong(java.sql.ResultSet row, int column) throws java.sql.SQLException { long value = row.getLong(column); return row.wasNull() ? null : value; }
}
