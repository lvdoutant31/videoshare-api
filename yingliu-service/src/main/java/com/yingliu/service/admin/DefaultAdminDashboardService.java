package com.yingliu.service.admin;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class DefaultAdminDashboardService implements AdminDashboardService {
    private final JdbcTemplate jdbc;
    public DefaultAdminDashboardService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override public DashboardSnapshot snapshot(LocalDate date) {
        long totalVideos = count("SELECT COUNT(*) FROM video WHERE deleted=0");
        long publishedVideos = count("SELECT COUNT(*) FROM video WHERE deleted=0 AND publish_status='PUBLISHED'");
        long pendingComments = count("SELECT COUNT(*) FROM video_comment WHERE status='PENDING' AND deleted_at IS NULL");
        long pendingReports = count("SELECT COUNT(*) FROM content_report WHERE status='PENDING'");
        long totalUsers = count("SELECT COUNT(*) FROM sys_user WHERE deleted=0");
        long plays = sum("SELECT COALESCE(SUM(play_count),0) FROM video_stats");
        long likes = sum("SELECT COALESCE(SUM(like_count),0) FROM video_stats");
        List<DailyMetric> metrics = jdbc.query("SELECT metric_date,metric_name,metric_value FROM daily_metric WHERE metric_date BETWEEN ? AND ? ORDER BY metric_date,metric_name",
                (rs, n) -> new DailyMetric(rs.getDate(1).toLocalDate(), rs.getString(2), rs.getBigDecimal(3)), date.minusDays(6), date);
        return new DashboardSnapshot(totalVideos,publishedVideos,pendingComments,pendingReports,totalUsers,plays,likes,metrics);
    }

    @Override public Summary summary(LocalDate date) {
        DashboardSnapshot snapshot = snapshot(date);
        List<Metric> metrics = List.of(
                new Metric("plays", "播放量", Long.toString(snapshot.plays()), "累计", "positive"),
                new Metric("newUsers", "用户数", Long.toString(snapshot.totalUsers()), "累计", "neutral"),
                new Metric("pendingReviews", "待处理审核", Long.toString(snapshot.pendingComments() + snapshot.pendingReports()), "评论/举报", "warning"),
                new Metric("playFailureRate", "播放失败率", "0%", "暂无失败统计", "neutral"));
        List<RecentVideo> videos = jdbc.query("SELECT v.id,v.title,u.nickname,v.publish_status,vs.play_count,v.published_at FROM video v JOIN sys_user u ON u.id=v.author_id LEFT JOIN video_stats vs ON vs.video_id=v.id WHERE v.deleted=0 ORDER BY v.created_at DESC LIMIT 5",
                (rs, n) -> new RecentVideo(Long.toString(rs.getLong(1)), rs.getString(2), rs.getString(3), videoStatus(rs.getString(4)),
                        rs.getObject(5) == null ? null : rs.getLong(5), rs.getTimestamp(6) == null ? null : rs.getTimestamp(6).toInstant().toString()));
        return new Summary(OffsetDateTime.now(ZoneOffset.UTC).toString(), metrics, videos);
    }

    @Override public Trends trends(LocalDate date, int days) {
        LocalDate start = date.minusDays(days - 1L);
        List<TrendPoint> points = jdbc.query("SELECT d.metric_date,COALESCE(SUM(CASE WHEN d.metric_name IN ('plays','play_count') THEN d.metric_value ELSE 0 END),0),COALESCE(SUM(CASE WHEN d.metric_name IN ('likes','like_count','comments','comment_count','shares','share_count') THEN d.metric_value ELSE 0 END),0) FROM daily_metric d WHERE d.metric_date BETWEEN ? AND ? GROUP BY d.metric_date ORDER BY d.metric_date",
                (rs, n) -> new TrendPoint(rs.getDate(1).toLocalDate().toString(), rs.getLong(2), rs.getLong(3)), start, date);
        return new Trends(points);
    }

    @Override public Todos todos() {
        return new Todos(List.of(
                new Todo("videoReview", "待审核视频", count("SELECT COUNT(*) FROM video WHERE deleted=0 AND audit_status='PENDING'"), "warning"),
                new Todo("report", "待处理举报", count("SELECT COUNT(*) FROM content_report WHERE status='PENDING'"), "danger"),
                new Todo("transcodeFailure", "转码失败", count("SELECT COUNT(*) FROM video_transcode WHERE status='FAILED'"), "danger"),
                new Todo("storageWarning", "存储异常", count("SELECT COUNT(*) FROM media_resource WHERE backup_status IN ('FAILED','DELAYED')"), "warning")));
    }

    @Override public Storage storage() {
        Long bytes = jdbc.queryForObject("SELECT COALESCE(SUM(file_size),0) FROM media_resource", Long.class);
        String backup = count("SELECT COUNT(*) FROM media_resource WHERE backup_status='FAILED'") > 0 ? "failed"
                : count("SELECT COUNT(*) FROM media_resource WHERE backup_status='DELAYED'") > 0 ? "delayed" : "healthy";
        return new Storage("/media", 0D, backup, OffsetDateTime.now(ZoneOffset.UTC).toString());
    }

    private String videoStatus(String status) {
        return switch (status) {
            case "PUBLISHED" -> "published";
            case "OFF_SHELF" -> "offShelf";
            case "TRANSCODING" -> "transcoding";
            case "TRANSCODE_FAILED" -> "transcodeFailed";
            default -> "pendingReview";
        };
    }
    private long count(String sql) { return jdbc.queryForObject(sql, Long.class); }
    private long sum(String sql) { return jdbc.queryForObject(sql, Long.class); }
}
