package com.yingliu.service.app;

import com.yingliu.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 埋点上报（API §8）：批量接收白名单事件，按月写入 analytics_event_YYYYMM。
 * 敏感字段（令牌/手机号/IP/企微标识）拒绝入库。
 */
@Service
@Profile("!test")
public class AppAnalyticsService {
    private static final Logger log = LoggerFactory.getLogger(AppAnalyticsService.class);
    private static final int MAX_EVENTS = 50;

    private static final Set<String> ALLOWED_EVENTS = Set.of(
            "app_open", "login_method_selected", "wecom_auth_success", "wecom_auth_failed",
            "account_bind_success", "account_bind_failed", "video_expose", "video_play_start",
            "video_play_pause", "video_play_progress", "video_play_complete", "feed_swipe",
            "video_play_failed", "like_toggled", "favorite_toggled", "comment_panel_open",
            "comment_published", "follow_toggled", "share_clicked", "share_channel_selected",
            "internal_share_sent", "share_card_opened", "report_submitted", "profile_viewed");

    private static final Set<String> FORBIDDEN_PROPERTY_KEYS = Set.of(
            "token", "accessToken", "phone", "ip", "ipHash", "wecomUserId", "wecomCorpId", "password");

    private final JdbcTemplate jdbc;
    private final VideoWatchMetricsService watchMetrics;
    private final java.util.concurrent.atomic.AtomicLong idSeq = new java.util.concurrent.atomic.AtomicLong(0);

    public AppAnalyticsService(JdbcTemplate jdbc, VideoWatchMetricsService watchMetrics) {
        this.jdbc = jdbc;
        this.watchMetrics = watchMetrics;
    }

    /** 批量接收；逐条校验，非法事件整批拒绝（避免部分写入难以对账） */
    public void receive(List<EventInput> events) {
        if (events == null || events.isEmpty()) return;
        if (events.size() > MAX_EVENTS) {
            throw new BusinessException("VALIDATION_FAILED", HttpStatus.UNPROCESSABLE_ENTITY, "单次最多上报 " + MAX_EVENTS + " 条事件");
        }
        String table = "analytics_event_" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        ensureTable(table);
        for (EventInput event : events) {
            if (!ALLOWED_EVENTS.contains(event.eventName())) {
                throw new BusinessException("VALIDATION_FAILED", HttpStatus.UNPROCESSABLE_ENTITY, "事件名不在白名单: " + event.eventName());
            }
            if (event.properties() != null) {
                for (String key : event.properties().keySet()) {
                    if (FORBIDDEN_PROPERTY_KEYS.contains(key)) {
                        throw new BusinessException("VALIDATION_FAILED", HttpStatus.UNPROCESSABLE_ENTITY, "事件属性含敏感字段");
                    }
                }
            }
        }
        for (EventInput event : events) {
            insert(table, event);
            recordWatchProgress(event);
        }
        log.debug("埋点批次已写入：数量={}，数据表={}", events.size(), table);
    }

    private void recordWatchProgress(EventInput event) {
        if (!"video_play_progress".equals(event.eventName()) || event.videoId() == null || event.properties() == null) return;
        int watchedDeltaMs = number(event.properties().get("watchedDeltaMs"));
        int positionMs = number(event.properties().get("positionMs"));
        int durationMs = number(event.properties().get("durationMs"));
        watchMetrics.record(event.eventId(), event.playSessionId(), event.videoId(), event.userId(),
                event.anonymousSessionId(), watchedDeltaMs, positionMs, durationMs > 0 ? durationMs : null,
                parseOccurredAt(event.occurredAt()));
    }

    private static int number(Object value) {
        if (value instanceof Number number) return number.intValue();
        try {
            return value == null ? 0 : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private void insert(String table, EventInput event) {
        long id = (System.currentTimeMillis() << 22) ^ (idSeq.incrementAndGet() & 0x3FFFFF);
        jdbc.update("INSERT INTO " + table + " (id, event_name, occurred_at, received_at, anonymous_session_id, "
                        + "user_id, video_id, object_id, page_source, client_version, network_type, trace_id, properties) "
                        + "VALUES (?, ?, ?, NOW(3), ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, event.eventName(), parseOccurredAt(event.occurredAt()),
                event.anonymousSessionId(), event.userId(), event.videoId(), event.objectId(),
                event.pageSource(), event.clientVersion(), event.networkType(), event.traceId(),
                event.properties() == null ? null : toJson(event.properties()));
    }

    /** ISO 8601 UTC（如 2026-09-02T00:00:00Z）→ LocalDateTime（UTC），无法解析时回落当前时间 */
    private java.time.LocalDateTime parseOccurredAt(String value) {
        if (value == null || value.isBlank()) return java.time.LocalDateTime.now(java.time.Clock.systemUTC());
        try {
            return java.time.OffsetDateTime.parse(value)
                    .atZoneSameInstant(java.time.ZoneOffset.UTC).toLocalDateTime();
        } catch (Exception e) {
            return java.time.LocalDateTime.now(java.time.Clock.systemUTC());
        }
    }

    /** 当月事件表不存在时自动创建（迁移预建失败/跨月兜底） */
    private void ensureTable(String table) {
        jdbc.execute("CREATE TABLE IF NOT EXISTS " + table + " ("
                + "id BIGINT UNSIGNED NOT NULL, event_name VARCHAR(64) NOT NULL, occurred_at DATETIME(3) NOT NULL, "
                + "received_at DATETIME(3) NOT NULL, anonymous_session_id CHAR(36) NOT NULL, user_id BIGINT UNSIGNED NULL, "
                + "video_id BIGINT UNSIGNED NULL, object_id BIGINT UNSIGNED NULL, page_source VARCHAR(128) NULL, "
                + "client_version VARCHAR(64) NULL, network_type VARCHAR(24) NULL, trace_id CHAR(32) NULL, "
                + "properties JSON NULL, PRIMARY KEY(id), "
                + "KEY idx_occurred_event(occurred_at,event_name), KEY idx_video_time(video_id,occurred_at), "
                + "KEY idx_analytics_user_time(user_id,occurred_at)) "
                + "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci");
    }

    private String toJson(Map<String, Object> properties) {
        StringBuilder sb = new StringBuilder("{");
        properties.forEach((k, v) -> sb.append('"').append(k).append("\":\"").append(String.valueOf(v).replace("\"", "\\\"")).append("\","));
        if (sb.length() > 1) sb.setLength(sb.length() - 1);
        return sb.append('}').toString();
    }

    public record EventInput(String eventId, String playSessionId, String eventName, String occurredAt, String anonymousSessionId,
                             Long userId, Long videoId, Long objectId, String pageSource,
                             String clientVersion, String networkType, String traceId,
                             Map<String, Object> properties) { }
}
