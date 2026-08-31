-- 映流短视频系统 V1 初始结构。MySQL 8.0+ / InnoDB / utf8mb4。

CREATE TABLE sys_user (
  id BIGINT UNSIGNED NOT NULL, phone_ciphertext VARBINARY(512) NULL, phone_hmac CHAR(64) NULL,
  nickname VARCHAR(64) NOT NULL, avatar_media_id BIGINT UNSIGNED NULL, bio VARCHAR(256) NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'NORMAL', mute_until DATETIME(3) NULL, suspend_until DATETIME(3) NULL,
  violation_count INT UNSIGNED NOT NULL DEFAULT 0, like_received_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
  following_count BIGINT UNSIGNED NOT NULL DEFAULT 0, follower_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
  last_login_at DATETIME(3) NULL, deleted TINYINT(1) NOT NULL DEFAULT 0, deleted_at DATETIME(3) NULL,
  deleted_by BIGINT UNSIGNED NULL, created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id), UNIQUE KEY uk_phone_hmac (phone_hmac), KEY idx_status_created (status, created_at DESC), KEY idx_nickname (nickname)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE media_resource (
  id BIGINT UNSIGNED NOT NULL, resource_type VARCHAR(24) NOT NULL, storage_state VARCHAR(24) NOT NULL,
  relative_path VARCHAR(512) NOT NULL, content_type VARCHAR(128) NOT NULL, file_size BIGINT UNSIGNED NOT NULL,
  sha256 CHAR(64) NOT NULL, width INT UNSIGNED NULL, height INT UNSIGNED NULL, duration_ms INT UNSIGNED NULL,
  video_codec VARCHAR(32) NULL, audio_codec VARCHAR(32) NULL, bitrate_kbps INT UNSIGNED NULL,
  backup_status VARCHAR(24) NOT NULL DEFAULT 'PENDING', created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id), UNIQUE KEY uk_relative_path (relative_path), KEY idx_sha256 (sha256), KEY idx_state_type (storage_state, resource_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_admin_user (
  id BIGINT UNSIGNED NOT NULL, user_id BIGINT UNSIGNED NULL, username VARCHAR(64) NOT NULL,
  password_hash VARCHAR(255) NOT NULL, status VARCHAR(24) NOT NULL DEFAULT 'NORMAL', last_login_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id), UNIQUE KEY uk_username (username), KEY idx_user_id (user_id),
  CONSTRAINT fk_admin_user_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_role (
  id BIGINT UNSIGNED NOT NULL, role_code VARCHAR(64) NOT NULL, role_name VARCHAR(64) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'NORMAL', is_system TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id), UNIQUE KEY uk_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE sys_permission (
  id BIGINT UNSIGNED NOT NULL, permission_code VARCHAR(128) NOT NULL, permission_name VARCHAR(64) NOT NULL,
  resource_type VARCHAR(24) NOT NULL, resource_path VARCHAR(255) NULL, parent_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
  sort INT NOT NULL DEFAULT 0, created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id), UNIQUE KEY uk_permission_code (permission_code), KEY idx_parent_sort (parent_id, sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE sys_admin_user_role (
  admin_user_id BIGINT UNSIGNED NOT NULL, role_id BIGINT UNSIGNED NOT NULL, created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (admin_user_id, role_id), CONSTRAINT fk_aur_admin FOREIGN KEY (admin_user_id) REFERENCES sys_admin_user(id),
  CONSTRAINT fk_aur_role FOREIGN KEY (role_id) REFERENCES sys_role(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE sys_role_permission (
  role_id BIGINT UNSIGNED NOT NULL, permission_id BIGINT UNSIGNED NOT NULL, created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (role_id, permission_id), CONSTRAINT fk_rp_role FOREIGN KEY (role_id) REFERENCES sys_role(id),
  CONSTRAINT fk_rp_permission FOREIGN KEY (permission_id) REFERENCES sys_permission(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_user_wecom_identity (
  id BIGINT UNSIGNED NOT NULL, user_id BIGINT UNSIGNED NOT NULL, corp_id VARCHAR(128) NOT NULL,
  wecom_user_id_ciphertext VARBINARY(512) NOT NULL, wecom_user_id_hmac CHAR(64) NOT NULL,
  display_name VARCHAR(128) NULL, bind_status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE', bound_at DATETIME(3) NOT NULL,
  unbound_at DATETIME(3) NULL, created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id), UNIQUE KEY uk_corp_member (corp_id, wecom_user_id_hmac), KEY idx_user_status (user_id, bind_status),
  CONSTRAINT fk_wecom_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE sys_login_log (
  id BIGINT UNSIGNED NOT NULL, principal_type VARCHAR(24) NOT NULL, principal_id BIGINT UNSIGNED NULL,
  login_method VARCHAR(24) NOT NULL, success TINYINT(1) NOT NULL, fail_code VARCHAR(64) NULL,
  ip_hash CHAR(64) NULL, device_hash CHAR(64) NULL, created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id), KEY idx_principal_time (principal_id, created_at DESC), KEY idx_created_at (created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE sys_user_agreement (
  id BIGINT UNSIGNED NOT NULL, user_id BIGINT UNSIGNED NOT NULL, agreement_type VARCHAR(32) NOT NULL,
  version VARCHAR(64) NOT NULL, agreed_at DATETIME(3) NOT NULL, ip_hash CHAR(64) NULL,
  PRIMARY KEY (id), UNIQUE KEY uk_user_agreement (user_id, agreement_type, version),
  CONSTRAINT fk_agreement_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE sys_verification_record (
  id BIGINT UNSIGNED NOT NULL, target_hmac CHAR(64) NOT NULL, purpose VARCHAR(32) NOT NULL,
  status VARCHAR(24) NOT NULL, expires_at DATETIME(3) NOT NULL, created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id), KEY idx_target_purpose (target_hmac, purpose, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE content_category (
  id BIGINT UNSIGNED NOT NULL, name VARCHAR(64) NOT NULL, icon VARCHAR(128) NULL, sort INT NOT NULL DEFAULT 0,
  status VARCHAR(24) NOT NULL DEFAULT 'ENABLED', deleted TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY(id), UNIQUE KEY uk_category_name(name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE content_tag (
  id BIGINT UNSIGNED NOT NULL, name VARCHAR(64) NOT NULL, status VARCHAR(24) NOT NULL DEFAULT 'ENABLED',
  heat_score DECIMAL(20,6) NOT NULL DEFAULT 0, merged_to_id BIGINT UNSIGNED NULL, deleted TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY(id), UNIQUE KEY uk_tag_name(name), KEY idx_merged_to(merged_to_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE video (
  id BIGINT UNSIGNED NOT NULL, author_id BIGINT UNSIGNED NOT NULL, title VARCHAR(120) NOT NULL,
  description VARCHAR(2000) NULL, source_note VARCHAR(128) NULL, cover_media_id BIGINT UNSIGNED NULL,
  original_media_id BIGINT UNSIGNED NOT NULL, audit_status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
  publish_status VARCHAR(24) NOT NULL DEFAULT 'UNPUBLISHED', visibility VARCHAR(24) NOT NULL DEFAULT 'PUBLIC',
  risk_level VARCHAR(16) NOT NULL DEFAULT 'NONE', manual_weight INT NOT NULL DEFAULT 0, scheduled_at DATETIME(3) NULL,
  published_at DATETIME(3) NULL, off_shelf_at DATETIME(3) NULL, off_shelf_reason VARCHAR(512) NULL,
  current_version INT UNSIGNED NOT NULL DEFAULT 1, deleted TINYINT(1) NOT NULL DEFAULT 0, deleted_at DATETIME(3) NULL,
  deleted_by BIGINT UNSIGNED NULL, created_by BIGINT UNSIGNED NOT NULL, updated_by BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY(id), KEY idx_feed(publish_status,audit_status,visibility,published_at DESC),
  KEY idx_author_status(author_id,publish_status,published_at DESC), KEY idx_audit_queue(audit_status,risk_level DESC,updated_at),
  KEY idx_schedule(publish_status,scheduled_at), CONSTRAINT fk_video_author FOREIGN KEY(author_id) REFERENCES sys_user(id),
  CONSTRAINT fk_video_cover FOREIGN KEY(cover_media_id) REFERENCES media_resource(id),
  CONSTRAINT fk_video_original FOREIGN KEY(original_media_id) REFERENCES media_resource(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE video_transcode (
  id BIGINT UNSIGNED NOT NULL, video_id BIGINT UNSIGNED NOT NULL, media_id BIGINT UNSIGNED NULL,
  definition VARCHAR(16) NOT NULL, status VARCHAR(24) NOT NULL DEFAULT 'PENDING', error_code VARCHAR(64) NULL,
  error_message VARCHAR(1000) NULL, started_at DATETIME(3) NULL, finished_at DATETIME(3) NULL,
  retry_count INT UNSIGNED NOT NULL DEFAULT 0, created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY(id), UNIQUE KEY uk_video_definition(video_id,definition), KEY idx_transcode_status(status,created_at),
  CONSTRAINT fk_transcode_video FOREIGN KEY(video_id) REFERENCES video(id), CONSTRAINT fk_transcode_media FOREIGN KEY(media_id) REFERENCES media_resource(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE video_category_map (
  video_id BIGINT UNSIGNED NOT NULL, category_id BIGINT UNSIGNED NOT NULL, PRIMARY KEY(video_id,category_id),
  CONSTRAINT fk_vcm_video FOREIGN KEY(video_id) REFERENCES video(id), CONSTRAINT fk_vcm_category FOREIGN KEY(category_id) REFERENCES content_category(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE video_tag_map (
  video_id BIGINT UNSIGNED NOT NULL, tag_id BIGINT UNSIGNED NOT NULL, PRIMARY KEY(video_id,tag_id), KEY idx_tag_video(tag_id,video_id),
  CONSTRAINT fk_vtm_video FOREIGN KEY(video_id) REFERENCES video(id), CONSTRAINT fk_vtm_tag FOREIGN KEY(tag_id) REFERENCES content_tag(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE media_upload_session (
  id BIGINT UNSIGNED NOT NULL, uploader_admin_id BIGINT UNSIGNED NOT NULL, file_name VARCHAR(255) NOT NULL,
  total_size BIGINT UNSIGNED NOT NULL, chunk_size INT UNSIGNED NOT NULL, chunk_count INT UNSIGNED NOT NULL,
  expected_sha256 CHAR(64) NOT NULL, status VARCHAR(24) NOT NULL DEFAULT 'UPLOADING', temp_relative_dir VARCHAR(512) NOT NULL,
  expires_at DATETIME(3) NOT NULL, created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY(id), KEY idx_upload_expiry(status,expires_at), CONSTRAINT fk_upload_admin FOREIGN KEY(uploader_admin_id) REFERENCES sys_admin_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE video_audit_record (
  id BIGINT UNSIGNED NOT NULL, video_id BIGINT UNSIGNED NOT NULL, video_version INT UNSIGNED NOT NULL,
  reviewer_admin_id BIGINT UNSIGNED NULL, action VARCHAR(24) NOT NULL, reason VARCHAR(1000) NULL,
  machine_risk_snapshot JSON NULL, claimed_at DATETIME(3) NULL, reviewed_at DATETIME(3) NULL, created_at DATETIME(3) NOT NULL,
  PRIMARY KEY(id), KEY idx_audit_video(video_id,created_at DESC), CONSTRAINT fk_audit_video FOREIGN KEY(video_id) REFERENCES video(id),
  CONSTRAINT fk_audit_reviewer FOREIGN KEY(reviewer_admin_id) REFERENCES sys_admin_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE video_publish_schedule (
  id BIGINT UNSIGNED NOT NULL, video_id BIGINT UNSIGNED NOT NULL, scheduled_at DATETIME(3) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'PENDING', executed_at DATETIME(3) NULL, failure_reason VARCHAR(1000) NULL,
  created_by BIGINT UNSIGNED NOT NULL, created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY(id), KEY idx_pending(status,scheduled_at), CONSTRAINT fk_schedule_video FOREIGN KEY(video_id) REFERENCES video(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE user_video_like (user_id BIGINT UNSIGNED NOT NULL, video_id BIGINT UNSIGNED NOT NULL, created_at DATETIME(3) NOT NULL,
 PRIMARY KEY(user_id,video_id), KEY idx_like_video(video_id), CONSTRAINT fk_like_user FOREIGN KEY(user_id) REFERENCES sys_user(id), CONSTRAINT fk_like_video FOREIGN KEY(video_id) REFERENCES video(id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE user_video_favorite (user_id BIGINT UNSIGNED NOT NULL, video_id BIGINT UNSIGNED NOT NULL, created_at DATETIME(3) NOT NULL,
 PRIMARY KEY(user_id,video_id), KEY idx_favorite_video(video_id), CONSTRAINT fk_favorite_user FOREIGN KEY(user_id) REFERENCES sys_user(id), CONSTRAINT fk_favorite_video FOREIGN KEY(video_id) REFERENCES video(id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE user_follow (follower_id BIGINT UNSIGNED NOT NULL, followee_id BIGINT UNSIGNED NOT NULL, created_at DATETIME(3) NOT NULL,
 PRIMARY KEY(follower_id,followee_id), KEY idx_followee(followee_id,created_at DESC), CONSTRAINT fk_follow_follower FOREIGN KEY(follower_id) REFERENCES sys_user(id), CONSTRAINT fk_follow_followee FOREIGN KEY(followee_id) REFERENCES sys_user(id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE video_comment (
 id BIGINT UNSIGNED NOT NULL, video_id BIGINT UNSIGNED NOT NULL, user_id BIGINT UNSIGNED NOT NULL, root_comment_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
 reply_to_comment_id BIGINT UNSIGNED NULL, content VARCHAR(1000) NOT NULL, status VARCHAR(24) NOT NULL DEFAULT 'PENDING', risk_level VARCHAR(16) NOT NULL DEFAULT 'NONE',
 like_count BIGINT UNSIGNED NOT NULL DEFAULT 0, reply_count BIGINT UNSIGNED NOT NULL DEFAULT 0, hot_score DECIMAL(20,6) NOT NULL DEFAULT 0,
 deleted_at DATETIME(3) NULL, deleted_by BIGINT UNSIGNED NULL, delete_reason VARCHAR(512) NULL, created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
 PRIMARY KEY(id), KEY idx_video_root_hot(video_id,root_comment_id,status,hot_score DESC,id DESC), KEY idx_root_created(root_comment_id,status,created_at), KEY idx_user_created(user_id,created_at DESC),
 CONSTRAINT fk_comment_video FOREIGN KEY(video_id) REFERENCES video(id), CONSTRAINT fk_comment_user FOREIGN KEY(user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE user_comment_like (user_id BIGINT UNSIGNED NOT NULL, comment_id BIGINT UNSIGNED NOT NULL, created_at DATETIME(3) NOT NULL,
 PRIMARY KEY(user_id,comment_id), KEY idx_comment_like(comment_id), CONSTRAINT fk_comment_like_user FOREIGN KEY(user_id) REFERENCES sys_user(id), CONSTRAINT fk_comment_like_comment FOREIGN KEY(comment_id) REFERENCES video_comment(id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE video_stats (
 video_id BIGINT UNSIGNED NOT NULL, exposure_count BIGINT UNSIGNED NOT NULL DEFAULT 0, play_count BIGINT UNSIGNED NOT NULL DEFAULT 0, effective_play_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
 complete_play_count BIGINT UNSIGNED NOT NULL DEFAULT 0, play_duration_ms BIGINT UNSIGNED NOT NULL DEFAULT 0, like_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
 favorite_count BIGINT UNSIGNED NOT NULL DEFAULT 0, comment_count BIGINT UNSIGNED NOT NULL DEFAULT 0, share_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
 report_count BIGINT UNSIGNED NOT NULL DEFAULT 0, updated_at DATETIME(3) NOT NULL, PRIMARY KEY(video_id), CONSTRAINT fk_stats_video FOREIGN KEY(video_id) REFERENCES video(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE user_video_history (user_id BIGINT UNSIGNED NOT NULL, video_id BIGINT UNSIGNED NOT NULL, last_viewed_at DATETIME(3) NOT NULL, view_count INT UNSIGNED NOT NULL DEFAULT 1, last_progress_ms INT UNSIGNED NOT NULL DEFAULT 0,
 PRIMARY KEY(user_id,video_id), KEY idx_user_recent(user_id,last_viewed_at DESC), CONSTRAINT fk_history_user FOREIGN KEY(user_id) REFERENCES sys_user(id), CONSTRAINT fk_history_video FOREIGN KEY(video_id) REFERENCES video(id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE video_share_inbox (
 id BIGINT UNSIGNED NOT NULL, batch_id BIGINT UNSIGNED NOT NULL, sender_id BIGINT UNSIGNED NOT NULL, recipient_id BIGINT UNSIGNED NOT NULL, video_id BIGINT UNSIGNED NOT NULL,
 status VARCHAR(24) NOT NULL DEFAULT 'DELIVERED', invalid_reason VARCHAR(128) NULL, sent_at DATETIME(3) NOT NULL, opened_at DATETIME(3) NULL,
 PRIMARY KEY(id), KEY idx_recipient(recipient_id,status,sent_at DESC), KEY idx_share_video(video_id,status), KEY idx_sender_recent(sender_id,sent_at DESC),
 CONSTRAINT fk_share_sender FOREIGN KEY(sender_id) REFERENCES sys_user(id), CONSTRAINT fk_share_recipient FOREIGN KEY(recipient_id) REFERENCES sys_user(id), CONSTRAINT fk_share_video FOREIGN KEY(video_id) REFERENCES video(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE video_share_event (id BIGINT UNSIGNED NOT NULL, video_id BIGINT UNSIGNED NOT NULL, user_id BIGINT UNSIGNED NULL, channel VARCHAR(24) NOT NULL, event_type VARCHAR(24) NOT NULL, trace_id CHAR(32) NULL, created_at DATETIME(3) NOT NULL,
 PRIMARY KEY(id), KEY idx_share_event_video(video_id,created_at DESC), KEY idx_share_event_user(user_id,created_at DESC)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE user_video_feedback (user_id BIGINT UNSIGNED NOT NULL, video_id BIGINT UNSIGNED NOT NULL, feedback_type VARCHAR(24) NOT NULL, created_at DATETIME(3) NOT NULL,
 PRIMARY KEY(user_id,video_id), CONSTRAINT fk_feedback_user FOREIGN KEY(user_id) REFERENCES sys_user(id), CONSTRAINT fk_feedback_video FOREIGN KEY(video_id) REFERENCES video(id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE content_report (id BIGINT UNSIGNED NOT NULL, reporter_id BIGINT UNSIGNED NOT NULL, target_type VARCHAR(24) NOT NULL, target_id BIGINT UNSIGNED NOT NULL, reason_code VARCHAR(64) NOT NULL, description VARCHAR(1000) NULL, target_snapshot JSON NULL, status VARCHAR(24) NOT NULL DEFAULT 'PENDING', risk_level VARCHAR(16) NOT NULL DEFAULT 'NONE', assignee_admin_id BIGINT UNSIGNED NULL, resolution VARCHAR(64) NULL, resolution_note VARCHAR(1000) NULL, resolved_at DATETIME(3) NULL, created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
 PRIMARY KEY(id), KEY idx_report_queue(status,risk_level DESC,created_at), KEY idx_report_target(target_type,target_id,created_at DESC), KEY idx_reporter(reporter_id,created_at DESC), CONSTRAINT fk_reporter FOREIGN KEY(reporter_id) REFERENCES sys_user(id), CONSTRAINT fk_report_assignee FOREIGN KEY(assignee_admin_id) REFERENCES sys_admin_user(id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE user_penalty (id BIGINT UNSIGNED NOT NULL, user_id BIGINT UNSIGNED NOT NULL, penalty_type VARCHAR(24) NOT NULL, reason VARCHAR(512) NOT NULL, source_type VARCHAR(24) NULL, source_id BIGINT UNSIGNED NULL, starts_at DATETIME(3) NOT NULL, ends_at DATETIME(3) NULL, status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE', issued_by BIGINT UNSIGNED NOT NULL, revoked_by BIGINT UNSIGNED NULL, revoke_reason VARCHAR(512) NULL, created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
 PRIMARY KEY(id), KEY idx_user_active(user_id,status,ends_at), KEY idx_penalty_expiry(status,ends_at), CONSTRAINT fk_penalty_user FOREIGN KEY(user_id) REFERENCES sys_user(id), CONSTRAINT fk_penalty_issuer FOREIGN KEY(issued_by) REFERENCES sys_admin_user(id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE sys_sensitive_word (id BIGINT UNSIGNED NOT NULL, word VARCHAR(128) NOT NULL, match_type VARCHAR(24) NOT NULL, action VARCHAR(24) NOT NULL, status VARCHAR(24) NOT NULL DEFAULT 'ENABLED', created_by BIGINT UNSIGNED NOT NULL, created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL, PRIMARY KEY(id), UNIQUE KEY uk_sensitive_word(word)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE sys_report_reason (id BIGINT UNSIGNED NOT NULL, reason_code VARCHAR(64) NOT NULL, reason_name VARCHAR(128) NOT NULL, target_types JSON NOT NULL, sort INT NOT NULL DEFAULT 0, status VARCHAR(24) NOT NULL DEFAULT 'ENABLED', created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL, PRIMARY KEY(id), UNIQUE KEY uk_reason_code(reason_code)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE sys_config (config_key VARCHAR(128) NOT NULL, config_value_ciphertext TEXT NULL, value_type VARCHAR(24) NOT NULL, version INT UNSIGNED NOT NULL DEFAULT 1, is_secret TINYINT(1) NOT NULL DEFAULT 0, updated_by BIGINT UNSIGNED NOT NULL DEFAULT 0, updated_at DATETIME(3) NOT NULL, PRIMARY KEY(config_key)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE sys_config_history (id BIGINT UNSIGNED NOT NULL, config_key VARCHAR(128) NOT NULL, before_value_ciphertext TEXT NULL, after_value_ciphertext TEXT NULL, version INT UNSIGNED NOT NULL, changed_by BIGINT UNSIGNED NOT NULL, changed_at DATETIME(3) NOT NULL, PRIMARY KEY(id), KEY idx_config_version(config_key,version)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE sys_audit_log (id BIGINT UNSIGNED NOT NULL, actor_type VARCHAR(24) NOT NULL, actor_id BIGINT UNSIGNED NOT NULL, action VARCHAR(128) NOT NULL, target_type VARCHAR(24) NOT NULL, target_id BIGINT UNSIGNED NOT NULL, reason VARCHAR(512) NULL, before_snapshot JSON NULL, after_snapshot JSON NULL, result VARCHAR(24) NOT NULL, trace_id CHAR(32) NULL, ip_hash CHAR(64) NULL, created_at DATETIME(3) NOT NULL, PRIMARY KEY(id), KEY idx_actor_time(actor_id,created_at DESC), KEY idx_target_time(target_type,target_id,created_at DESC), KEY idx_action_time(action,created_at DESC)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE sys_async_task (id BIGINT UNSIGNED NOT NULL, task_type VARCHAR(48) NOT NULL, business_type VARCHAR(48) NOT NULL, business_id BIGINT UNSIGNED NOT NULL, payload JSON NULL, status VARCHAR(24) NOT NULL DEFAULT 'PENDING', retry_count INT UNSIGNED NOT NULL DEFAULT 0, max_retries INT UNSIGNED NOT NULL DEFAULT 3, next_retry_at DATETIME(3) NULL, last_error_code VARCHAR(64) NULL, last_error_message VARCHAR(1000) NULL, started_at DATETIME(3) NULL, finished_at DATETIME(3) NULL, created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL, PRIMARY KEY(id), UNIQUE KEY uk_task_idempotency(task_type,business_type,business_id), KEY idx_poll(status,next_retry_at,created_at)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE daily_metric (metric_date DATE NOT NULL, metric_scope VARCHAR(24) NOT NULL, scope_id BIGINT UNSIGNED NOT NULL, metric_name VARCHAR(64) NOT NULL, metric_value DECIMAL(24,6) NOT NULL, calculated_at DATETIME(3) NOT NULL, PRIMARY KEY(metric_date,metric_scope,scope_id,metric_name)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE analytics_event_202608 (id BIGINT UNSIGNED NOT NULL, event_name VARCHAR(64) NOT NULL, occurred_at DATETIME(3) NOT NULL, received_at DATETIME(3) NOT NULL, anonymous_session_id CHAR(36) NOT NULL, user_id BIGINT UNSIGNED NULL, video_id BIGINT UNSIGNED NULL, object_id BIGINT UNSIGNED NULL, page_source VARCHAR(128) NULL, client_version VARCHAR(64) NULL, network_type VARCHAR(24) NULL, trace_id CHAR(32) NULL, properties JSON NULL, PRIMARY KEY(id), KEY idx_occurred_event(occurred_at,event_name), KEY idx_video_time(video_id,occurred_at), KEY idx_analytics_user_time(user_id,occurred_at)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 基础角色。ID 使用固定保留值，业务实体仍由应用生成 Snowflake ID。
INSERT INTO sys_role (id, role_code, role_name, status, is_system, created_at, updated_at) VALUES
(1, 'SUPER_ADMIN', '超级管理员', 'NORMAL', 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
(2, 'OPERATOR', '内容运营', 'NORMAL', 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
(3, 'REVIEWER', '审核员', 'NORMAL', 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
(4, 'CUSTOMER_SERVICE', '客服', 'NORMAL', 1, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

INSERT INTO sys_config (config_key, config_value_ciphertext, value_type, version, is_secret, updated_by, updated_at) VALUES
('media.upload.max-size-bytes', '2147483648', 'NUMBER', 1, 0, 0, UTC_TIMESTAMP(3)),
('media.upload.max-duration-seconds', '600', 'NUMBER', 1, 0, 0, UTC_TIMESTAMP(3)),
('media.disk.warning-threshold', '80', 'NUMBER', 1, 0, 0, UTC_TIMESTAMP(3)),
('media.disk.stop-upload-threshold', '90', 'NUMBER', 1, 0, 0, UTC_TIMESTAMP(3)),
('comment.enabled', 'true', 'BOOLEAN', 1, 0, 0, UTC_TIMESTAMP(3));
