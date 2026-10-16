package com.yingliu.service.admin;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class AdminModerationService {
    private final JdbcTemplate jdbc;
    public AdminModerationService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Page<CommentItem> comments(long page, long size, String status) {
        String filter = status == null || status.isBlank() ? "" : " WHERE c.status = ?";
        Object[] args = filter.isEmpty() ? new Object[]{size, (page - 1) * size} : new Object[]{status, size, (page - 1) * size};
        List<CommentItem> items = jdbc.query("SELECT c.id,c.video_id,c.user_id,c.content,c.status,c.risk_level,c.like_count,c.created_at FROM video_comment c" + filter + " ORDER BY c.created_at DESC LIMIT ? OFFSET ?", (rs, n) -> new CommentItem(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getLong(7), rs.getTimestamp(8).toInstant()), args);
        long total = filter.isEmpty() ? jdbc.queryForObject("SELECT COUNT(*) FROM video_comment", Long.class) : jdbc.queryForObject("SELECT COUNT(*) FROM video_comment c WHERE c.status = ?", Long.class, status);
        return new Page<>(items, total);
    }

    @Transactional public CommentDetail moderateComment(long id, long adminId, String status, String reason) {
        int changed = jdbc.update("UPDATE video_comment SET status=?,deleted_at=CASE WHEN ?='DELETED' THEN CURRENT_TIMESTAMP(3) ELSE NULL END,deleted_by=CASE WHEN ?='DELETED' THEN ? ELSE NULL END,delete_reason=?,updated_at=CURRENT_TIMESTAMP(3) WHERE id=?", status, status, status, adminId, reason, id);
        if (changed != 1) throw new IllegalArgumentException("comment not found");
        audit(adminId, "COMMENT_" + status, "COMMENT", id, reason);
        return commentDetail(id);
    }
    @Transactional public void batchModerateComments(List<Long> ids, long adminId, String status, String reason) {
        for (Long id : ids) moderateComment(id, adminId, status, reason);
    }

    public CommentDetail commentDetail(long id) {
        return jdbc.queryForObject("SELECT c.id,c.video_id,v.title,u.nickname,c.content,c.status,c.risk_level,c.like_count,c.reply_count,c.created_at,c.delete_reason,c.reply_to_comment_id " +
                        "FROM video_comment c JOIN video v ON v.id=c.video_id JOIN sys_user u ON u.id=c.user_id WHERE c.id=?",
                (rs, n) -> new CommentDetail(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getLong(8), rs.getLong(9), rs.getTimestamp(10).toInstant(), rs.getString(11), nullableLong(rs, 12), replies(id), history("COMMENT", id)), id);
    }

    public Page<ReportItem> reports(long page, long size, String status) {
        String filter = status == null || status.isBlank() ? "" : " WHERE r.status = ?";
        Object[] args = filter.isEmpty() ? new Object[]{size, (page - 1) * size} : new Object[]{status, size, (page - 1) * size};
        List<ReportItem> items = jdbc.query("SELECT r.id,r.reporter_id,r.target_type,r.target_id,r.reason_code,r.description,r.status,r.risk_level,r.created_at FROM content_report r" + filter + " ORDER BY r.created_at DESC LIMIT ? OFFSET ?", (rs, n) -> new ReportItem(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getLong(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getTimestamp(9).toInstant()), args);
        long total = filter.isEmpty() ? jdbc.queryForObject("SELECT COUNT(*) FROM content_report", Long.class) : jdbc.queryForObject("SELECT COUNT(*) FROM content_report r WHERE r.status = ?", Long.class, status);
        return new Page<>(items, total);
    }
    @Transactional public ReportDetail claimReport(long id, long adminId) {
        int changed = jdbc.update("UPDATE content_report SET status='PROCESSING',assignee_admin_id=?,updated_at=CURRENT_TIMESTAMP(3) WHERE id=? AND status='PENDING'", adminId, id);
        if (changed != 1) throw new IllegalArgumentException("pending report not found");
        audit(adminId, "REPORT_CLAIM", "REPORT", id, null);
        return reportDetail(id);
    }
    @Transactional public ReportDetail resolveReport(long id, long adminId, String resolution, String note) {
        int changed = jdbc.update("UPDATE content_report SET status='RESOLVED',assignee_admin_id=?,resolution=?,resolution_note=?,resolved_at=CURRENT_TIMESTAMP(3),updated_at=CURRENT_TIMESTAMP(3) WHERE id=? AND status IN ('PENDING','PROCESSING')", adminId, resolution, note, id);
        if (changed != 1) throw new IllegalArgumentException("open report not found");
        audit(adminId, "REPORT_RESOLVE", "REPORT", id, note);
        return reportDetail(id);
    }
    @Transactional public ReportDetail rejectReport(long id, long adminId, String note) {
        int changed = jdbc.update("UPDATE content_report SET status='REJECTED',assignee_admin_id=?,resolution='REJECTED',resolution_note=?,resolved_at=CURRENT_TIMESTAMP(3),updated_at=CURRENT_TIMESTAMP(3) WHERE id=? AND status IN ('PENDING','PROCESSING')", adminId, note, id);
        if (changed != 1) throw new IllegalArgumentException("open report not found");
        audit(adminId, "REPORT_REJECT", "REPORT", id, note);
        return reportDetail(id);
    }

    public ReportDetail reportDetail(long id) {
        return jdbc.queryForObject("SELECT r.id,r.target_type,r.target_id,COALESCE(r.description,r.reason_code),u.nickname,r.reason_code,r.risk_level,r.status,a.username,r.created_at,r.resolution,r.target_snapshot " +
                        "FROM content_report r JOIN sys_user u ON u.id=r.reporter_id LEFT JOIN sys_admin_user a ON a.id=r.assignee_admin_id WHERE r.id=?",
                (rs, n) -> new ReportDetail(rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9), rs.getTimestamp(10).toInstant(), rs.getString(11), rs.getString(12), history("REPORT", id)), id);
    }

    private List<CommentReply> replies(long id) {
        return jdbc.query("SELECT c.id,u.nickname,c.content,c.created_at FROM video_comment c JOIN sys_user u ON u.id=c.user_id WHERE c.reply_to_comment_id=? ORDER BY c.created_at", (rs, n) -> new CommentReply(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getTimestamp(4).toInstant()), id);
    }
    private List<ModerationHistory> history(String targetType, long id) {
        return jdbc.query("SELECT l.action,COALESCE(a.username,CAST(l.actor_id AS CHAR)),l.created_at,l.reason FROM sys_audit_log l LEFT JOIN sys_admin_user a ON a.id=l.actor_id WHERE l.target_type=? AND l.target_id=? ORDER BY l.created_at", (rs, n) -> new ModerationHistory(rs.getString(1), rs.getString(2), rs.getTimestamp(3).toInstant(), rs.getString(4)), targetType, id);
    }
    private static Long nullableLong(java.sql.ResultSet rs, int column) throws java.sql.SQLException { long value = rs.getLong(column); return rs.wasNull() ? null : value; }
    private void audit(long adminId, String action, String type, long id, String reason) { jdbc.update("INSERT INTO sys_audit_log (id,actor_type,actor_id,action,target_type,target_id,reason,result,created_at) VALUES (?, 'ADMIN', ?, ?, ?, ?, ?, 'SUCCESS', CURRENT_TIMESTAMP(3))", Math.abs(System.nanoTime()), adminId, action, type, id, reason); }

    public record Page<T>(List<T> items, long total) { }
    public record CommentItem(long id, long videoId, long userId, String content, String status, String riskLevel, long likeCount, Instant createdAt) { }
    public record CommentReply(long id, String author, String content, Instant createdAt) { }
    public record ModerationHistory(String action, String operator, Instant createdAt, String reason) { }
    public record CommentDetail(long id, long videoId, String videoTitle, String author, String content, String status, String riskLevel, long likeCount, long replyCount, Instant createdAt, String handledReason, Long parentCommentId, List<CommentReply> replies, List<ModerationHistory> history) { }
    public record ReportItem(long id, long reporterId, String targetType, long targetId, String reasonCode, String description, String status, String riskLevel, Instant createdAt) { }
    public record ReportDetail(long id, String targetType, long targetId, String targetSummary, String reporter, String reason, String riskLevel, String status, String assignee, Instant createdAt, String resolution, String targetSnapshot, List<ModerationHistory> history) { }
}
