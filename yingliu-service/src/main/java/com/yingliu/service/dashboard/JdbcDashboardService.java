package com.yingliu.service.dashboard;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class JdbcDashboardService implements DashboardService {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MM-dd");
    private final JdbcTemplate jdbc;
    public JdbcDashboardService(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public Summary summary() {
        long plays=metric("EFFECTIVE_PLAYS"), users=metric("NEW_USERS"), pending=count("SELECT COUNT(*) FROM video WHERE deleted=0 AND audit_status='PENDING_REVIEW'");
        double failure=metricDecimal("PLAY_FAILURE_RATE");
        List<Metric> metrics=List.of(new Metric("plays","今日播放",String.format("%,d",plays),"","positive"),new Metric("newUsers","今日新增用户",String.format("%,d",users),"","positive"),new Metric("pendingReviews","待审核视频",String.valueOf(pending),"",pending>0?"warning":"neutral"),new Metric("playFailureRate","播放失败率",String.format("%.1f%%",failure),"",failure>1?"danger":"neutral"));
        List<RecentVideo> recent=query("SELECT v.id,v.title,u.nickname,v.audit_status,v.publish_status,v.published_at,COALESCE(s.play_count,0) FROM video v JOIN sys_user u ON u.id=v.author_id LEFT JOIN video_stats s ON s.video_id=v.id WHERE v.deleted=0 ORDER BY v.updated_at DESC LIMIT 10",(r,n)->new RecentVideo(String.valueOf(r.getLong(1)),r.getString(2),r.getString(3),status(r.getString(4),r.getString(5)),r.getLong(7),r.getTimestamp(6)==null?null:r.getTimestamp(6).toLocalDateTime()));
        return new Summary(LocalDateTime.now(ZoneOffset.UTC).toString(),metrics,recent);
    }
    @Override public Trend trend(String range) { String safe="30d".equals(range)?"30d":"7d"; int days="30d".equals(safe)?30:7; List<TrendPoint> points=query("SELECT metric_date,COALESCE(SUM(CASE WHEN metric_name='EFFECTIVE_PLAYS' THEN metric_value ELSE 0 END),0),COALESCE(SUM(CASE WHEN metric_name='INTERACTIONS' THEN metric_value ELSE 0 END),0) FROM daily_metric WHERE metric_scope='PLATFORM' AND metric_date >= CURRENT_DATE - INTERVAL ? DAY GROUP BY metric_date ORDER BY metric_date",(r,n)->new TrendPoint(r.getDate(1).toLocalDate().format(DATE),r.getBigDecimal(2).longValue(),r.getBigDecimal(3).longValue()),days-1); return new Trend(safe,points); }
    @Override public List<Todo> todos() { long reviews=count("SELECT COUNT(*) FROM video WHERE deleted=0 AND audit_status='PENDING_REVIEW'"), reports=count("SELECT COUNT(*) FROM content_report WHERE status IN ('PENDING','PROCESSING')"), transcodes=count("SELECT COUNT(*) FROM sys_async_task WHERE task_type='TRANSCODE' AND status='FAILED'"); int storage=storage().usedPercent(); return List.of(new Todo("videoReview","视频待审核",reviews,reviews>0?"warning":"neutral"),new Todo("report","举报待处置",reports,reports>0?"warning":"neutral"),new Todo("transcodeFailure","转码失败任务",transcodes,transcodes>0?"danger":"neutral"),new Todo("storageWarning","存储容量预警",storage>=85?1:0,storage>=85?"danger":"neutral")); }
    @Override public Storage storage() { int used=0; try { FileStore fs=Files.getFileStore(Path.of("/media")); used=(int)Math.round((1d-(double)fs.getUsableSpace()/fs.getTotalSpace())*100); } catch(Exception ignored) { } String backup=queryOne("SELECT backup_status FROM media_resource ORDER BY updated_at DESC LIMIT 1",String.class,"healthy"); return new Storage("/media",Math.max(0,Math.min(100,used)),"FAILED".equals(backup)?"failed":"DELAYED".equals(backup)?"delayed":"healthy",LocalDateTime.now(ZoneOffset.UTC).toString()); }
    private long metric(String n){return queryOne("SELECT COALESCE(SUM(metric_value),0) FROM daily_metric WHERE metric_scope='PLATFORM' AND metric_date=CURRENT_DATE AND metric_name=?",Long.class,0L,n);} private double metricDecimal(String n){return queryOne("SELECT COALESCE(SUM(metric_value),0) FROM daily_metric WHERE metric_scope='PLATFORM' AND metric_date=CURRENT_DATE AND metric_name=?",Double.class,0d,n);} private long count(String s){return queryOne(s,Long.class,0L);} private String status(String a,String p){if("PUBLISHED".equals(p))return"published";if("PENDING_REVIEW".equals(a))return"pendingReview";if("TRANSCODING".equals(a))return"transcoding";if("OFF_SHELF".equals(p))return"offShelf";return"transcodeFailed";} private <T>T queryOne(String s,Class<T> c,T f,Object...a){try{T v=jdbc.queryForObject(s,c,a);return v==null?f:v;}catch(DataAccessException e){return f;}} private <T>List<T> query(String s,org.springframework.jdbc.core.RowMapper<T> m,Object...a){try{return jdbc.query(s,m,a);}catch(DataAccessException e){return List.of();}}
}
