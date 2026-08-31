package com.yingliu.service.moderation;

import com.yingliu.service.content.shared.ServicePage;
import org.springframework.jdbc.core.JdbcTemplate;
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
  if(status!=null){where+=" AND status=?";a.add(status);} if(videoId!=null){where+=" AND video_id=?";a.add(videoId);} if(keyword!=null&&!keyword.isBlank()){where+=" AND content LIKE ?";a.add("%"+keyword+"%");}
  long total=db.queryForObject("SELECT COUNT(*) FROM video_comment"+where,Long.class,a.toArray()); a.add((p-1)*s);a.add(s);
  return new ServicePage<>(db.query("SELECT id,video_id,user_id,content,status,risk_level,like_count,reply_count,created_at,updated_at FROM video_comment"+where+" ORDER BY created_at DESC,id DESC LIMIT ?,?",(r,n)->new Comment(r.getLong(1),r.getLong(2),r.getLong(3),r.getString(4),r.getString(5),r.getString(6),r.getLong(7),r.getLong(8),r.getTimestamp(9).toLocalDateTime(),r.getTimestamp(10).toLocalDateTime()),a.toArray()),total);
 }
 public Comment comment(long id){return db.queryForObject("SELECT id,video_id,user_id,content,status,risk_level,like_count,reply_count,created_at,updated_at FROM video_comment WHERE id=?",(r,n)->new Comment(r.getLong(1),r.getLong(2),r.getLong(3),r.getString(4),r.getString(5),r.getString(6),r.getLong(7),r.getLong(8),r.getTimestamp(9).toLocalDateTime(),r.getTimestamp(10).toLocalDateTime()),id);}
 @Transactional public Comment hideComment(long id,long actor,String reason){db.update("UPDATE video_comment SET status='HIDDEN',deleted_by=?,delete_reason=?,deleted_at=NOW(3),updated_at=NOW(3) WHERE id=? AND status<>'DELETED'",actor,reason,id);return comment(id);}
 @Transactional public Comment restoreComment(long id,long actor){db.update("UPDATE video_comment SET status='PENDING',deleted_by=NULL,delete_reason=NULL,deleted_at=NULL,updated_at=NOW(3) WHERE id=?",id);return comment(id);}
 @Transactional public Comment deleteComment(long id,long actor,String reason){db.update("UPDATE video_comment SET status='DELETED',deleted_by=?,delete_reason=?,deleted_at=NOW(3),updated_at=NOW(3) WHERE id=?",actor,reason,id);return comment(id);}
 @Transactional public void deleteComments(List<Long> ids,long actor,String reason){ids.forEach(id->deleteComment(id,actor,reason));}
 public ServicePage<Report> reports(long p,long s,String status,String targetType){String w=" WHERE 1=1";java.util.List<Object>a=new java.util.ArrayList<>();if(status!=null){w+=" AND status=?";a.add(status);}if(targetType!=null){w+=" AND target_type=?";a.add(targetType);}long t=db.queryForObject("SELECT COUNT(*) FROM content_report"+w,Long.class,a.toArray());a.add((p-1)*s);a.add(s);return new ServicePage<>(db.query("SELECT id,reporter_id,target_type,target_id,reason_code,description,status,risk_level,assignee_admin_id,resolution,resolution_note,resolved_at,created_at,updated_at FROM content_report"+w+" ORDER BY created_at DESC,id DESC LIMIT ?,?",this::reportRow,a.toArray()),t);}
 public Report report(long id){return db.queryForObject("SELECT id,reporter_id,target_type,target_id,reason_code,description,status,risk_level,assignee_admin_id,resolution,resolution_note,resolved_at,created_at,updated_at FROM content_report WHERE id=?",this::reportRow,id);}
 private Report reportRow(java.sql.ResultSet r,int n)throws java.sql.SQLException{return new Report(r.getLong(1),r.getLong(2),r.getString(3),r.getLong(4),r.getString(5),r.getString(6),r.getString(7),r.getString(8),(Long)r.getObject(9),r.getString(10),r.getString(11),r.getTimestamp(12)==null?null:r.getTimestamp(12).toLocalDateTime(),r.getTimestamp(13).toLocalDateTime(),r.getTimestamp(14).toLocalDateTime());}
 public void claimReport(long id,long actor){db.update("UPDATE content_report SET status='CLAIMED',assignee_admin_id=?,updated_at=NOW(3) WHERE id=? AND status='PENDING'",actor,id);}
 public void resolveReport(long id,long actor,String resolution,String note){db.update("UPDATE content_report SET status='RESOLVED',resolution=?,resolution_note=?,resolved_at=NOW(3),assignee_admin_id=?,updated_at=NOW(3) WHERE id=? AND status IN ('PENDING','CLAIMED')",resolution,note,actor,id);}
 public void rejectReport(long id,long actor,String note){resolveReport(id,actor,"REJECTED",note);}
}
