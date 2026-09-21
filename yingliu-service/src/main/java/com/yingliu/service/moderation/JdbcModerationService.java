package com.yingliu.service.moderation;

import com.yingliu.common.exception.BusinessException;
import com.yingliu.service.content.shared.ServicePage;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class JdbcModerationService implements ModerationService {
    private final JdbcTemplate db;

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
    public ServicePage<Report> reports(long page, long size, String status, String targetType) {
        String where = " WHERE 1=1"; List<Object> args = new ArrayList<>();
        if (status != null) { where += " AND status=?"; args.add(status); }
        if (targetType != null) { where += " AND target_type=?"; args.add(targetType); }
        long total = db.queryForObject("SELECT COUNT(*) FROM content_report" + where, Long.class, args.toArray());
        args.add((page - 1) * size); args.add(size);
        return new ServicePage<>(db.query("SELECT id,reporter_id,target_type,target_id,reason_code,description,status,risk_level,assignee_admin_id,resolution,resolution_note,resolved_at,created_at,updated_at FROM content_report" + where + " ORDER BY created_at DESC,id DESC LIMIT ?,?", this::reportRow, args.toArray()), total);
    }

    @Override
    public Report report(long id) { return db.queryForObject("SELECT id,reporter_id,target_type,target_id,reason_code,description,status,risk_level,assignee_admin_id,resolution,resolution_note,resolved_at,created_at,updated_at FROM content_report WHERE id=?", this::reportRow, id); }

    private Report reportRow(java.sql.ResultSet row, int ignored) throws java.sql.SQLException { return new Report(row.getLong(1), row.getLong(2), row.getString(3), row.getLong(4), row.getString(5), row.getString(6), row.getString(7), row.getString(8), (Long) row.getObject(9), row.getString(10), row.getString(11), row.getTimestamp(12) == null ? null : row.getTimestamp(12).toLocalDateTime(), row.getTimestamp(13).toLocalDateTime(), row.getTimestamp(14).toLocalDateTime()); }

    @Override
    public void claimReport(long id, long actor) { db.update("UPDATE content_report SET status='CLAIMED',assignee_admin_id=?,updated_at=NOW(3) WHERE id=? AND status='PENDING'", actor, id); }

    @Override
    public void resolveReport(long id, long actor, String resolution, String note) { db.update("UPDATE content_report SET status='RESOLVED',resolution=?,resolution_note=?,resolved_at=NOW(3),assignee_admin_id=?,updated_at=NOW(3) WHERE id=? AND status IN ('PENDING','CLAIMED')", resolution, note, actor, id); }

    @Override
    public void rejectReport(long id, long actor, String note) { resolveReport(id, actor, "REJECTED", note); }

    private List<CommentReply> replies(long id) { return db.query("SELECT c.id,u.nickname,c.content,c.created_at FROM video_comment c JOIN sys_user u ON u.id=c.user_id WHERE c.reply_to_comment_id=? ORDER BY c.created_at,c.id", (row, ignored) -> new CommentReply(row.getLong(1), row.getString(2), row.getString(3), row.getTimestamp(4).toLocalDateTime()), id); }

    private List<ModerationHistory> history(long id, String author, LocalDateTime createdAt) {
        List<ModerationHistory> entries = new ArrayList<>();
        entries.add(new ModerationHistory("COMMENT_CREATED", author, createdAt, null));
        entries.addAll(db.query("SELECT l.action,COALESCE(a.username,CONCAT('管理员 #',l.actor_id)),l.created_at,l.reason FROM sys_audit_log l LEFT JOIN sys_admin_user a ON a.id=l.actor_id WHERE l.target_type='COMMENT' AND l.target_id=? ORDER BY l.created_at,l.id", (row, ignored) -> new ModerationHistory(row.getString(1), row.getString(2), row.getTimestamp(3).toLocalDateTime(), row.getString(4)), id));
        return entries;
    }

    private void audit(long actor, String action, long id, String reason) { db.update("INSERT INTO sys_audit_log (id,actor_type,actor_id,action,target_type,target_id,reason,result,created_at) VALUES (?, 'ADMIN', ?, ?, 'COMMENT', ?, ?, 'SUCCESS', NOW(3))", Math.abs(System.nanoTime()), actor, action, id, reason); }

    private static Long nullableLong(java.sql.ResultSet row, int column) throws java.sql.SQLException { long value = row.getLong(column); return row.wasNull() ? null : value; }
}
