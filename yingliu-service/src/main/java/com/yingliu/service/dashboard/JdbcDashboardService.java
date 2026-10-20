package com.yingliu.service.dashboard;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class JdbcDashboardService implements DashboardService {
    private static final int STORAGE_WARNING_PERCENT = 80;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MM-dd");
    private static final ZoneId DASHBOARD_ZONE = ZoneOffset.UTC;
    private static final String RECENT_VIDEOS_SQL = """
            SELECT v.id, v.title, u.nickname, v.audit_status, v.publish_status,
                   v.published_at, v.updated_at
            FROM video v JOIN sys_user u ON u.id = v.author_id
            WHERE v.deleted = 0
            ORDER BY v.updated_at DESC
            LIMIT 10
            """;
    private static final String VIDEO_STATS_SQL = """
            SELECT stats.video_id, stats.effective_play_count,
                   COALESCE(like_count, 0) + COALESCE(favorite_count, 0)
                     + COALESCE(comment_count, 0) + COALESCE(share_count, 0) AS interaction_count
            FROM video_stats stats
            JOIN (SELECT id FROM video WHERE deleted = 0 ORDER BY updated_at DESC LIMIT 10) recent
              ON recent.id = stats.video_id
            """;
    private static final String VIDEO_TRANSCODE_SQL = """
            SELECT transcode.video_id,
                   CASE
                     WHEN SUM(transcode.status = 'FAILED') > 0 THEN 'FAILED'
                     WHEN SUM(transcode.status IN ('PROCESSING', 'PENDING')) > 0 THEN 'PROCESSING'
                     ELSE MAX(transcode.status)
                   END AS transcode_status
            FROM video_transcode transcode
            JOIN (SELECT id FROM video WHERE deleted = 0 ORDER BY updated_at DESC LIMIT 10) recent
              ON recent.id = transcode.video_id
            GROUP BY transcode.video_id
            """;

    private final JdbcTemplate jdbc;
    private final Path storageRoot;

    @Autowired
    public JdbcDashboardService(JdbcTemplate jdbc,
                                @Value("${media.storage-root:document/media}") String storageRoot) {
        this.jdbc = jdbc;
        this.storageRoot = Path.of(storageRoot).toAbsolutePath().normalize();
    }

    JdbcDashboardService(JdbcTemplate jdbc) {
        this(jdbc, "/media");
    }

    @Override
    public Summary summary() {
        LocalDate today = dashboardDate();
        LocalDate yesterday = today.minusDays(1);
        Timestamp start = dayStart(today);
        Timestamp tomorrow = dayStart(today.plusDays(1));
        Timestamp yesterdayStart = dayStart(yesterday);

        long todayPlays = scalar("SELECT COALESCE(SUM(effective_play_count), 0) FROM video_watch_daily WHERE metric_date = ?", today);
        long yesterdayPlays = scalar("SELECT COALESCE(SUM(effective_play_count), 0) FROM video_watch_daily WHERE metric_date = ?", yesterday);
        long todayUsers = scalar("SELECT COUNT(*) FROM sys_user WHERE deleted = 0 AND created_at >= ? AND created_at < ?", start, tomorrow);
        long yesterdayUsers = scalar("SELECT COUNT(*) FROM sys_user WHERE deleted = 0 AND created_at >= ? AND created_at < ?", yesterdayStart, start);
        long publishedVideos = scalar("SELECT COUNT(*) FROM video WHERE deleted = 0 AND publish_status = 'PUBLISHED'");
        long todayPublished = scalar("SELECT COUNT(*) FROM video WHERE deleted = 0 AND publish_status = 'PUBLISHED' AND published_at >= ? AND published_at < ?", start, tomorrow);
        long todayLikes = scalar("SELECT COUNT(*) FROM user_video_like WHERE created_at >= ? AND created_at < ?", start, tomorrow);
        long todayFavorites = scalar("SELECT COUNT(*) FROM user_video_favorite WHERE created_at >= ? AND created_at < ?", start, tomorrow);
        long todayComments = scalar("SELECT COUNT(*) FROM video_comment WHERE status = 'NORMAL' AND deleted_at IS NULL AND created_at >= ? AND created_at < ?", start, tomorrow);
        long interactions = todayLikes + todayFavorites + todayComments;

        List<Metric> metrics = List.of(
                new Metric("plays", "今日有效播放", format(todayPlays), comparisonLabel(todayPlays, yesterdayPlays), activityTone(todayPlays, yesterdayPlays)),
                new Metric("newUsers", "今日新增用户", format(todayUsers), comparisonLabel(todayUsers, yesterdayUsers), activityTone(todayUsers, yesterdayUsers)),
                new Metric("publishedVideos", "已发布视频", format(publishedVideos), "今日发布 " + format(todayPublished) + " 条", "neutral"),
                new Metric("interactions", "今日互动", format(interactions),
                        "点赞 " + format(todayLikes) + " · 收藏 " + format(todayFavorites) + " · 评论 " + format(todayComments),
                        interactions > 0 ? "positive" : "neutral")
        );
        return new Summary(LocalDateTime.now(ZoneOffset.UTC).toString(), metrics, recentVideos());
    }

    @Override
    public Trend trend(String range) {
        String safeRange = "30d".equals(range) ? "30d" : "7d";
        int days = "30d".equals(safeRange) ? 30 : 7;
        LocalDate end = dashboardDate();
        LocalDate start = end.minusDays(days - 1L);
        Timestamp startTime = dayStart(start);
        Timestamp endTime = dayStart(end.plusDays(1));

        Map<LocalDate, Long> plays = dailyCounts(
                "SELECT metric_date, COALESCE(SUM(effective_play_count), 0) FROM video_watch_daily WHERE metric_date BETWEEN ? AND ? GROUP BY metric_date",
                java.sql.Date.valueOf(start), java.sql.Date.valueOf(end));
        Map<LocalDate, Long> likes = dailyCounts(
                "SELECT DATE(created_at), COUNT(*) FROM user_video_like WHERE created_at >= ? AND created_at < ? GROUP BY DATE(created_at)",
                startTime, endTime);
        Map<LocalDate, Long> favorites = dailyCounts(
                "SELECT DATE(created_at), COUNT(*) FROM user_video_favorite WHERE created_at >= ? AND created_at < ? GROUP BY DATE(created_at)",
                startTime, endTime);
        Map<LocalDate, Long> comments = dailyCounts(
                "SELECT DATE(created_at), COUNT(*) FROM video_comment WHERE status = 'NORMAL' AND deleted_at IS NULL AND created_at >= ? AND created_at < ? GROUP BY DATE(created_at)",
                startTime, endTime);

        List<TrendPoint> points = start.datesUntil(end.plusDays(1)).map(date -> new TrendPoint(
                date.format(DATE), plays.getOrDefault(date, 0L),
                likes.getOrDefault(date, 0L) + favorites.getOrDefault(date, 0L) + comments.getOrDefault(date, 0L)
        )).toList();
        return new Trend(safeRange, points);
    }

    @Override
    public List<Todo> todos() {
        long reviews = count("SELECT COUNT(*) FROM video WHERE deleted = 0 AND audit_status = 'PENDING_REVIEW'");
        long reports = count("SELECT COUNT(*) FROM content_report WHERE status IN ('PENDING', 'PROCESSING')");
        int storageUsed = storage().usedPercent();
        return List.of(
                new Todo("videoReview", "视频待审核", reviews, reviews > 0 ? "warning" : "neutral", "/reviews"),
                new Todo("report", "举报待处置", reports, reports > 0 ? "warning" : "neutral", "/reports"),
                new Todo("storageWarning", "存储容量预警", storageUsed >= STORAGE_WARNING_PERCENT ? 1 : 0,
                        storageUsed >= 90 ? "danger" : storageUsed >= STORAGE_WARNING_PERCENT ? "warning" : "neutral", null)
        );
    }

    @Override
    public Storage storage() {
        int usedPercent = storageUsedPercent();
        return new Storage("/media", usedPercent, "unknown", null);
    }

    private List<RecentVideo> recentVideos() {
        List<RecentVideoBase> videos = query(RECENT_VIDEOS_SQL, (rs, rowNum) -> new RecentVideoBase(
                rs.getLong("id"), rs.getString("title"), rs.getString("nickname"),
                rs.getString("audit_status"), rs.getString("publish_status"),
                toLocalDateTime(rs.getTimestamp("published_at")), toLocalDateTime(rs.getTimestamp("updated_at"))));
        if (videos.isEmpty()) return List.of();
        Map<Long, String> transcodeStates = query(VIDEO_TRANSCODE_SQL, (rs, rowNum) ->
                        new TranscodeState(rs.getLong("video_id"), rs.getString("transcode_status")))
                .stream().collect(Collectors.toMap(TranscodeState::videoId, TranscodeState::status, (left, right) -> right));
        Map<Long, VideoStats> stats = query(VIDEO_STATS_SQL, (rs, rowNum) -> new VideoStats(
                        rs.getLong("video_id"), rs.getLong("effective_play_count"), rs.getLong("interaction_count")))
                .stream().collect(Collectors.toMap(VideoStats::videoId, Function.identity(), (left, right) -> right));
        return videos.stream().map(video -> {
            VideoStats stat = stats.get(video.id());
            return new RecentVideo(String.valueOf(video.id()), video.title(), video.author(),
                    status(video.auditStatus(), video.publishStatus(), transcodeStates.get(video.id())),
                    stat == null ? 0L : stat.playCount(),
                    stat == null ? 0L : stat.interactionCount(), video.publishedAt(), video.updatedAt());
        }).toList();
    }

    private Map<LocalDate, Long> dailyCounts(String sql, Object... args) {
        return query(sql, (rs, rowNum) -> new DailyCount(rs.getDate(1).toLocalDate(), rs.getLong(2)), args)
                .stream().collect(Collectors.toMap(DailyCount::date, DailyCount::count, Long::sum, HashMap::new));
    }

    private LocalDate dashboardDate() {
        return LocalDate.now(DASHBOARD_ZONE);
    }

    private Timestamp dayStart(LocalDate date) {
        return Timestamp.from(date.atStartOfDay(DASHBOARD_ZONE).toInstant());
    }

    private int storageUsedPercent() {
        try {
            if (!Files.exists(storageRoot)) return 0;
            FileStore fileStore = Files.getFileStore(storageRoot);
            long totalSpace = fileStore.getTotalSpace();
            if (totalSpace <= 0) return 0;
            int used = (int) Math.round((1d - (double) fileStore.getUsableSpace() / totalSpace) * 100);
            return Math.max(0, Math.min(100, used));
        } catch (Exception ignored) {
            return 0;
        }
    }

    private long scalar(String sql, Object... args) {
        Number value = jdbc.queryForObject(sql, Number.class, args);
        return value == null ? 0 : value.longValue();
    }

    private long count(String sql) {
        return scalar(sql);
    }

    private <T> T queryOne(String sql, RowMapper<T> mapper, T fallback, Object... args) {
        T value = jdbc.queryForObject(sql, mapper, args);
        return value == null ? fallback : value;
    }

    private <T> List<T> query(String sql, RowMapper<T> mapper, Object... args) {
        List<T> values = jdbc.query(sql, mapper, args);
        return values == null ? List.of() : values;
    }

    private String comparisonLabel(long today, long yesterday) {
        if (yesterday == 0) return today == 0 ? "昨日 0" : "昨日 0 · 今日新增 " + format(today);
        return String.format(Locale.ROOT, "较昨日 %+.1f%%", (today - yesterday) * 100d / yesterday);
    }

    private String activityTone(long today, long yesterday) {
        if (today == 0 && yesterday == 0) return "neutral";
        return today >= yesterday ? "positive" : "warning";
    }

    private String status(String auditStatus, String publishStatus, String transcodeStatus) {
        if ("FAILED".equals(transcodeStatus)) return "transcodeFailed";
        if ("PROCESSING".equals(transcodeStatus)) return "transcoding";
        if ("PUBLISHED".equals(publishStatus)) return "published";
        if ("OFF_SHELF".equals(publishStatus)) return "offShelf";
        if ("TRANSCODING".equals(publishStatus)) return "transcoding";
        if ("TRANSCODE_FAILED".equals(publishStatus)) return "transcodeFailed";
        if ("PENDING_REVIEW".equals(auditStatus)) return "pendingReview";
        if ("REJECTED".equals(auditStatus)) return "rejected";
        return "draft";
    }

    private String format(long value) {
        return String.format(Locale.ROOT, "%,d", value);
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private record DailyCount(LocalDate date, long count) { }
    private record RecentVideoBase(long id, String title, String author, String auditStatus, String publishStatus,
                                   LocalDateTime publishedAt, LocalDateTime updatedAt) { }
    private record VideoStats(long videoId, long playCount, long interactionCount) { }
    private record TranscodeState(long videoId, String status) { }
}
