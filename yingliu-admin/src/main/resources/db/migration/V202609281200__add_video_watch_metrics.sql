CREATE TABLE video_watch_event (
    event_id CHAR(36) NOT NULL,
    play_session_id CHAR(36) NOT NULL,
    video_id BIGINT UNSIGNED NOT NULL,
    watched_delta_ms INT UNSIGNED NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    PRIMARY KEY (event_id),
    KEY idx_watch_event_session (play_session_id),
    KEY idx_watch_event_video_time (video_id, occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE video_watch_session (
    play_session_id CHAR(36) NOT NULL,
    video_id BIGINT UNSIGNED NOT NULL,
    user_id BIGINT UNSIGNED NULL,
    anonymous_session_id CHAR(36) NOT NULL,
    watch_time_ms BIGINT UNSIGNED NOT NULL DEFAULT 0,
    max_position_ms INT UNSIGNED NOT NULL DEFAULT 0,
    duration_ms INT UNSIGNED NULL,
    effective_play TINYINT(1) NOT NULL DEFAULT 0,
    completed TINYINT(1) NOT NULL DEFAULT 0,
    started_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (play_session_id),
    KEY idx_watch_session_video_time (video_id, started_at),
    KEY idx_watch_session_user_time (user_id, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE video_watch_daily (
    metric_date DATE NOT NULL,
    video_id BIGINT UNSIGNED NOT NULL,
    effective_play_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    watch_time_ms BIGINT UNSIGNED NOT NULL DEFAULT 0,
    complete_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
    calculated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (metric_date, video_id),
    KEY idx_watch_daily_video_date (video_id, metric_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
