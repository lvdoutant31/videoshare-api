package com.yingliu.service.app;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/** 幂等累计播放器上报的真实墙钟观看时长。 */
@Service
@Profile("!test")
public class VideoWatchMetricsService {
    static final int MAX_DELTA_MS = 15_000;
    static final long EFFECTIVE_PLAY_MS = 3_000;

    private final JdbcTemplate jdbc;

    public VideoWatchMetricsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void record(String eventId, String playSessionId, long videoId, Long userId,
                       String anonymousSessionId, int watchedDeltaMs, int positionMs,
                       Integer durationMs, LocalDateTime occurredAt) {
        if (!validId(eventId) || !validId(playSessionId) || videoId <= 0 || watchedDeltaMs <= 0
                || anonymousSessionId == null || anonymousSessionId.isBlank()) return;
        Integer publicVideo = jdbc.queryForObject("SELECT COUNT(*) FROM video WHERE id = ? AND deleted = 0 "
                + "AND audit_status = 'APPROVED' AND publish_status = 'PUBLISHED' AND visibility = 'PUBLIC'",
                Integer.class, videoId);
        if (publicVideo == null || publicVideo == 0) return;

        int delta = Math.min(watchedDeltaMs, MAX_DELTA_MS);
        int inserted = jdbc.update("INSERT IGNORE INTO video_watch_event "
                        + "(event_id, play_session_id, video_id, watched_delta_ms, occurred_at) VALUES (?, ?, ?, ?, ?)",
                eventId, playSessionId, videoId, delta, occurredAt);
        if (inserted == 0) return;

        jdbc.update("INSERT IGNORE INTO video_watch_session "
                        + "(play_session_id, video_id, user_id, anonymous_session_id, watch_time_ms, max_position_ms, "
                        + "duration_ms, effective_play, completed, started_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, 0, 0, ?, 0, 0, ?, ?)",
                playSessionId, videoId, userId, anonymousSessionId, positive(durationMs), occurredAt, occurredAt);

        SessionState before = jdbc.queryForObject("SELECT video_id, watch_time_ms, effective_play, completed "
                        + "FROM video_watch_session WHERE play_session_id = ? FOR UPDATE",
                (rs, rowNum) -> new SessionState(rs.getLong("video_id"), rs.getLong("watch_time_ms"),
                        rs.getBoolean("effective_play"), rs.getBoolean("completed")), playSessionId);
        if (before == null || before.videoId() != videoId) return;

        long nextWatchTime = before.watchTimeMs() + delta;
        boolean effective = before.effective() || nextWatchTime >= EFFECTIVE_PLAY_MS;
        Integer safeDuration = positive(durationMs);
        int safePosition = Math.max(0, positionMs);
        boolean completed = before.completed() || safeDuration != null
                && safePosition >= Math.ceil(safeDuration * 0.9d);
        int effectiveDelta = !before.effective() && effective ? 1 : 0;
        int completeDelta = !before.completed() && completed ? 1 : 0;

        jdbc.update("UPDATE video_watch_session SET watch_time_ms = ?, max_position_ms = GREATEST(max_position_ms, ?), "
                        + "duration_ms = COALESCE(?, duration_ms), effective_play = ?, completed = ?, updated_at = ? "
                        + "WHERE play_session_id = ?",
                nextWatchTime, safePosition, safeDuration, effective, completed, occurredAt, playSessionId);
        jdbc.update("INSERT INTO video_watch_daily "
                        + "(metric_date, video_id, effective_play_count, watch_time_ms, complete_count, calculated_at) "
                        + "VALUES (?, ?, ?, ?, ?, NOW(3)) ON DUPLICATE KEY UPDATE "
                        + "effective_play_count = effective_play_count + VALUES(effective_play_count), "
                        + "watch_time_ms = watch_time_ms + VALUES(watch_time_ms), "
                        + "complete_count = complete_count + VALUES(complete_count), calculated_at = NOW(3)",
                occurredAt.toLocalDate(), videoId, effectiveDelta, delta, completeDelta);
        if (effectiveDelta > 0) {
            jdbc.update("INSERT INTO video_stats "
                            + "(video_id, play_count, like_count, favorite_count, comment_count, updated_at) "
                            + "VALUES (?, 1, 0, 0, 0, NOW(3)) ON DUPLICATE KEY UPDATE "
                            + "play_count = play_count + 1, updated_at = NOW(3)", videoId);
        }
    }

    private static boolean validId(String value) {
        return value != null && value.length() == 36;
    }

    private static Integer positive(Integer value) {
        return value != null && value > 0 ? value : null;
    }

    private record SessionState(long videoId, long watchTimeMs, boolean effective, boolean completed) { }
}
