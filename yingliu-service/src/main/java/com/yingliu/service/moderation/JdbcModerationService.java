package com.yingliu.service.moderation;

import com.yingliu.service.content.shared.ServicePage;
import com.yingliu.common.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class JdbcModerationService implements ModerationService {
 private final JdbcTemplate db;
 public JdbcModerationService(JdbcTemplate db){this.db=db;}
 public ServicePage<Comment> comments(long p,long s,String status,Long videoId,String keyword){
  String where=" WHERE 1=1"; java.util.List<Object> a=new java.util.ArrayList<>();
  if(status!=null){where+=" AND c.status=?";a.add(status);} if(videoId!=null){where+=" AND c.video_id=?";a.add(videoId);} if(keyword!=null&&!keyword.isBlank()){where+=" AND c.content LIKE ?";a.add("%"+keyword+"%");}
  String from=" FROM video_comment c JOIN video v ON v.id=c.video_id JOIN sys_user u ON u.id=c.user_id";
  long total=db.queryForObject("SELECT COUNT(*)"+from+where,Long.class,a.toArray()); a.add((p-1)*s);a.add(s);
  return new ServicePage<>(db.query("SELECT c.id,c.video_id,v.title,c.user_id,u.nickname,c.content,c.status,c.risk_level,c.like_count,c.reply_count,c.created_at,c.updated_at"+from+where+" ORDER BY c.created_at DESC,c.id DESC LIMIT ?,?",this::commentRow,a.toArray()),total);
 }
 public Comment comment(long id){return db.queryForObject("SELECT c.id,c.video_id,v.title,c.user_id,u.nickname,c.content,c.status,c.risk_level,c.like_count,c.reply_count,c.created_at,c.updated_at FROM video_comment c JOIN video v ON v.id=c.video_id JOIN sys_user u ON u.id=c.user_id WHERE c.id=?",this::commentRow,id);}
 private Comment commentRow(java.sql.ResultSet r,int n)throws java.sql.SQLException{return new Comment(r.getLong(1),r.getLong(2),r.getString(3),r.getLong(4),r.getString(5),r.getString(6),r.getString(7),r.getString(8),r.getLong(9),r.getLong(10),r.getTimestamp(11).toLocalDateTime(),r.getTimestamp(12).toLocalDateTime());}
 @Transactional public Comment hideComment(long id,long actor,String reason){db.update("UPDATE video_comment SET status='HIDDEN',deleted_by=?,delete_reason=?,deleted_at=NOW(3),updated_at=NOW(3) WHERE id=? AND status<>'DELETED'",actor,reason,id);return comment(id);}
 @Transactional public Comment restoreComment(long id,long actor){
  Long videoId=db.queryForObject("SELECT video_id FROM video_comment WHERE id=?",Long.class,id);
  if(videoId==null){throw new BusinessException("RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND,"评论不存在");}
  Boolean videoDeleted=db.queryForObject("SELECT deleted=1 FROM video WHERE id=? FOR UPDATE",Boolean.class,videoId);
  if(Boolean.TRUE.equals(videoDeleted)){throw new BusinessException("CONFLICT",HttpStatus.CONFLICT,"视频已删除，评论不可恢复");}
  int updated=db.update("UPDATE video_comment SET status='PENDING',deleted_by=NULL,delete_reason=NULL,deleted_at=NULL,updated_at=NOW(3) WHERE id=? AND status='HIDDEN' AND video_id=?",id,videoId);
  if(updated==0){throw new BusinessException("CONFLICT",HttpStatus.CONFLICT,"评论状态已变化，不可恢复");}
  return comment(id);
 }
 @Transactional public Comment deleteComment(long id,long actor,String reason){db.update("UPDATE video_comment SET status='DELETED',deleted_by=?,delete_reason=?,deleted_at=NOW(3),updated_at=NOW(3) WHERE id=?",actor,reason,id);return comment(id);}
 @Transactional public void deleteComments(List<Long> ids,long actor,String reason){ids.forEach(id->deleteComment(id,actor,reason));}
 public ServicePage<Report> reports(long p,long s,String status,String targetType){String w=" WHERE 1=1";java.util.List<Object>a=new java.util.ArrayList<>();if(status!=null){w+=" AND status=?";a.add(status);}if(targetType!=null){w+=" AND target_type=?";a.add(targetType);}long t=db.queryForObject("SELECT COUNT(*) FROM content_report"+w,Long.class,a.toArray());a.add((p-1)*s);a.add(s);return new ServicePage<>(db.query("SELECT id,reporter_id,target_type,target_id,reason_code,description,status,risk_level,assignee_admin_id,resolution,resolution_note,resolved_at,created_at,updated_at FROM content_report"+w+" ORDER BY created_at DESC,id DESC LIMIT ?,?",this::reportRow,a.toArray()),t);}
 public Report report(long id){return db.queryForObject("SELECT id,reporter_id,target_type,target_id,reason_code,description,status,risk_level,assignee_admin_id,resolution,resolution_note,resolved_at,created_at,updated_at FROM content_report WHERE id=?",this::reportRow,id);}
 private Report reportRow(java.sql.ResultSet r,int n)throws java.sql.SQLException{return new Report(r.getLong(1),r.getLong(2),r.getString(3),r.getLong(4),r.getString(5),r.getString(6),r.getString(7),r.getString(8),(Long)r.getObject(9),r.getString(10),r.getString(11),r.getTimestamp(12)==null?null:r.getTimestamp(12).toLocalDateTime(),r.getTimestamp(13).toLocalDateTime(),r.getTimestamp(14).toLocalDateTime());}
 public void claimReport(long id,long actor){db.update("UPDATE content_report SET status='CLAIMED',assignee_admin_id=?,updated_at=NOW(3) WHERE id=? AND status='PENDING'",actor,id);}
 public void resolveReport(long id,long actor,String resolution,String note){db.update("UPDATE content_report SET status='RESOLVED',resolution=?,resolution_note=?,resolved_at=NOW(3),assignee_admin_id=?,updated_at=NOW(3) WHERE id=? AND status IN ('PENDING','CLAIMED')",resolution,note,actor,id);}
 public void rejectReport(long id,long actor,String note){resolveReport(id,actor,"REJECTED",note);}
}
