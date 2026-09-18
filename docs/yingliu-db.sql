/*
 Navicat Premium Data Transfer

 Source Server         : dev
 Source Server Type    : MySQL
 Source Server Version : 80035 (8.0.35)
 Source Host           : localhost:3306
 Source Schema         : yingliu-db

 Target Server Type    : MySQL
 Target Server Version : 80035 (8.0.35)
 File Encoding         : 65001

 Date: 09/09/2026 10:38:29
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for analytics_event_202608
-- ----------------------------
DROP TABLE IF EXISTS `analytics_event_202608`;
CREATE TABLE `analytics_event_202608`  (
  `id` bigint UNSIGNED NOT NULL,
  `event_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `occurred_at` datetime(3) NOT NULL,
  `received_at` datetime(3) NOT NULL,
  `anonymous_session_id` char(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `user_id` bigint UNSIGNED NULL DEFAULT NULL,
  `video_id` bigint UNSIGNED NULL DEFAULT NULL,
  `object_id` bigint UNSIGNED NULL DEFAULT NULL,
  `page_source` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `client_version` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `network_type` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `trace_id` char(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `properties` json NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_occurred_event`(`occurred_at` ASC, `event_name` ASC) USING BTREE,
  INDEX `idx_video_time`(`video_id` ASC, `occurred_at` ASC) USING BTREE,
  INDEX `idx_analytics_user_time`(`user_id` ASC, `occurred_at` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for analytics_event_202609
-- ----------------------------
DROP TABLE IF EXISTS `analytics_event_202609`;
CREATE TABLE `analytics_event_202609`  (
  `id` bigint UNSIGNED NOT NULL,
  `event_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `occurred_at` datetime(3) NOT NULL,
  `received_at` datetime(3) NOT NULL,
  `anonymous_session_id` char(36) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `user_id` bigint UNSIGNED NULL DEFAULT NULL,
  `video_id` bigint UNSIGNED NULL DEFAULT NULL,
  `object_id` bigint UNSIGNED NULL DEFAULT NULL,
  `page_source` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `client_version` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `network_type` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `trace_id` char(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `properties` json NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_occurred_event`(`occurred_at` ASC, `event_name` ASC) USING BTREE,
  INDEX `idx_video_time`(`video_id` ASC, `occurred_at` ASC) USING BTREE,
  INDEX `idx_analytics_user_time`(`user_id` ASC, `occurred_at` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for content_category
-- ----------------------------
DROP TABLE IF EXISTS `content_category`;
CREATE TABLE `content_category`  (
  `id` bigint UNSIGNED NOT NULL,
  `name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `icon` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `sort` int NOT NULL DEFAULT 0,
  `status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'ENABLED',
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_category_name`(`name` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for content_report
-- ----------------------------
DROP TABLE IF EXISTS `content_report`;
CREATE TABLE `content_report`  (
  `id` bigint UNSIGNED NOT NULL,
  `reporter_id` bigint UNSIGNED NOT NULL,
  `target_type` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `target_id` bigint UNSIGNED NOT NULL,
  `reason_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `description` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `target_snapshot` json NULL,
  `status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'PENDING',
  `risk_level` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'NONE',
  `assignee_admin_id` bigint UNSIGNED NULL DEFAULT NULL,
  `resolution` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `resolution_note` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `resolved_at` datetime(3) NULL DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_report_queue`(`status` ASC, `risk_level` DESC, `created_at` ASC) USING BTREE,
  INDEX `idx_report_target`(`target_type` ASC, `target_id` ASC, `created_at` DESC) USING BTREE,
  INDEX `idx_reporter`(`reporter_id` ASC, `created_at` DESC) USING BTREE,
  INDEX `fk_report_assignee`(`assignee_admin_id` ASC) USING BTREE,
  CONSTRAINT `fk_report_assignee` FOREIGN KEY (`assignee_admin_id`) REFERENCES `sys_admin_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_reporter` FOREIGN KEY (`reporter_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for content_tag
-- ----------------------------
DROP TABLE IF EXISTS `content_tag`;
CREATE TABLE `content_tag`  (
  `id` bigint UNSIGNED NOT NULL,
  `name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'ENABLED',
  `heat_score` decimal(20, 6) NOT NULL DEFAULT 0.000000,
  `merged_to_id` bigint UNSIGNED NULL DEFAULT NULL,
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_tag_name`(`name` ASC) USING BTREE,
  INDEX `idx_merged_to`(`merged_to_id` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for daily_metric
-- ----------------------------
DROP TABLE IF EXISTS `daily_metric`;
CREATE TABLE `daily_metric`  (
  `metric_date` date NOT NULL,
  `metric_scope` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `scope_id` bigint UNSIGNED NOT NULL,
  `metric_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `metric_value` decimal(24, 6) NOT NULL,
  `calculated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`metric_date`, `metric_scope`, `scope_id`, `metric_name`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for flyway_schema_history
-- ----------------------------
DROP TABLE IF EXISTS `flyway_schema_history`;
CREATE TABLE `flyway_schema_history`  (
  `installed_rank` int NOT NULL,
  `version` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `description` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `script` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `checksum` int NULL DEFAULT NULL,
  `installed_by` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `installed_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `execution_time` int NOT NULL,
  `success` tinyint(1) NOT NULL,
  PRIMARY KEY (`installed_rank`) USING BTREE,
  INDEX `flyway_schema_history_s_idx`(`success` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for media_resource
-- ----------------------------
DROP TABLE IF EXISTS `media_resource`;
CREATE TABLE `media_resource`  (
  `id` bigint UNSIGNED NOT NULL,
  `resource_type` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `storage_state` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `relative_path` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `url` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `content_type` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `file_size` bigint UNSIGNED NOT NULL,
  `sha256` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `width` int UNSIGNED NULL DEFAULT NULL,
  `height` int UNSIGNED NULL DEFAULT NULL,
  `duration_ms` int UNSIGNED NULL DEFAULT NULL,
  `video_codec` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `audio_codec` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `bitrate_kbps` int UNSIGNED NULL DEFAULT NULL,
  `backup_status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'PENDING',
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_relative_path`(`relative_path` ASC) USING BTREE,
  INDEX `idx_sha256`(`sha256` ASC) USING BTREE,
  INDEX `idx_state_type`(`storage_state` ASC, `resource_type` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for media_upload_session
-- ----------------------------
DROP TABLE IF EXISTS `media_upload_session`;
CREATE TABLE `media_upload_session`  (
  `id` bigint UNSIGNED NOT NULL,
  `uploader_admin_id` bigint UNSIGNED NOT NULL,
  `file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `total_size` bigint UNSIGNED NOT NULL,
  `chunk_size` int UNSIGNED NOT NULL,
  `chunk_count` int UNSIGNED NOT NULL,
  `expected_sha256` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'UPLOADING',
  `temp_relative_dir` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `expires_at` datetime(3) NOT NULL,
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_upload_expiry`(`status` ASC, `expires_at` ASC) USING BTREE,
  INDEX `fk_upload_admin`(`uploader_admin_id` ASC) USING BTREE,
  CONSTRAINT `fk_upload_admin` FOREIGN KEY (`uploader_admin_id`) REFERENCES `sys_admin_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sys_admin_confirmation
-- ----------------------------
DROP TABLE IF EXISTS `sys_admin_confirmation`;
CREATE TABLE `sys_admin_confirmation`  (
  `id` bigint UNSIGNED NOT NULL,
  `token_hash` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `actor_admin_id` bigint UNSIGNED NOT NULL,
  `action` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `target_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `target_id` bigint UNSIGNED NOT NULL,
  `expires_at` datetime(3) NOT NULL,
  `consumed_at` datetime(3) NULL DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_admin_confirmation_token_hash`(`token_hash` ASC) USING BTREE,
  INDEX `idx_admin_confirmation_consume`(`actor_admin_id` ASC, `action` ASC, `target_type` ASC, `target_id` ASC, `expires_at` ASC, `consumed_at` ASC) USING BTREE,
  CONSTRAINT `fk_admin_confirmation_actor` FOREIGN KEY (`actor_admin_id`) REFERENCES `sys_admin_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sys_admin_user
-- ----------------------------
DROP TABLE IF EXISTS `sys_admin_user`;
CREATE TABLE `sys_admin_user`  (
  `id` bigint UNSIGNED NOT NULL,
  `user_id` bigint UNSIGNED NULL DEFAULT NULL,
  `username` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `password_hash` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'NORMAL',
  `last_login_at` datetime(3) NULL DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_username`(`username` ASC) USING BTREE,
  INDEX `idx_user_id`(`user_id` ASC) USING BTREE,
  CONSTRAINT `fk_admin_user_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sys_admin_user_role
-- ----------------------------
DROP TABLE IF EXISTS `sys_admin_user_role`;
CREATE TABLE `sys_admin_user_role`  (
  `admin_user_id` bigint UNSIGNED NOT NULL,
  `role_id` bigint UNSIGNED NOT NULL,
  `created_at` datetime(3) NOT NULL,
  PRIMARY KEY (`admin_user_id`, `role_id`) USING BTREE,
  INDEX `fk_aur_role`(`role_id` ASC) USING BTREE,
  CONSTRAINT `fk_aur_admin` FOREIGN KEY (`admin_user_id`) REFERENCES `sys_admin_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_aur_role` FOREIGN KEY (`role_id`) REFERENCES `sys_role` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sys_async_task
-- ----------------------------
DROP TABLE IF EXISTS `sys_async_task`;
CREATE TABLE `sys_async_task`  (
  `id` bigint UNSIGNED NOT NULL,
  `task_type` varchar(48) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `business_type` varchar(48) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `business_id` bigint UNSIGNED NOT NULL,
  `payload` json NULL,
  `status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'PENDING',
  `retry_count` int UNSIGNED NOT NULL DEFAULT 0,
  `max_retries` int UNSIGNED NOT NULL DEFAULT 3,
  `next_retry_at` datetime(3) NULL DEFAULT NULL,
  `last_error_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `last_error_message` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `started_at` datetime(3) NULL DEFAULT NULL,
  `finished_at` datetime(3) NULL DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_task_idempotency`(`task_type` ASC, `business_type` ASC, `business_id` ASC) USING BTREE,
  INDEX `idx_poll`(`status` ASC, `next_retry_at` ASC, `created_at` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sys_audit_log
-- ----------------------------
DROP TABLE IF EXISTS `sys_audit_log`;
CREATE TABLE `sys_audit_log`  (
  `id` bigint UNSIGNED NOT NULL,
  `actor_type` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `actor_id` bigint UNSIGNED NOT NULL,
  `action` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `target_type` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `target_id` bigint UNSIGNED NOT NULL,
  `reason` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `before_snapshot` json NULL,
  `after_snapshot` json NULL,
  `result` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `trace_id` char(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `ip_hash` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_actor_time`(`actor_id` ASC, `created_at` DESC) USING BTREE,
  INDEX `idx_target_time`(`target_type` ASC, `target_id` ASC, `created_at` DESC) USING BTREE,
  INDEX `idx_action_time`(`action` ASC, `created_at` DESC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sys_config
-- ----------------------------
DROP TABLE IF EXISTS `sys_config`;
CREATE TABLE `sys_config`  (
  `config_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `config_value_ciphertext` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  `value_type` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `version` int UNSIGNED NOT NULL DEFAULT 1,
  `is_secret` tinyint(1) NOT NULL DEFAULT 0,
  `updated_by` bigint UNSIGNED NOT NULL DEFAULT 0,
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`config_key`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sys_config_history
-- ----------------------------
DROP TABLE IF EXISTS `sys_config_history`;
CREATE TABLE `sys_config_history`  (
  `id` bigint UNSIGNED NOT NULL,
  `config_key` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `before_value_ciphertext` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  `after_value_ciphertext` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL,
  `version` int UNSIGNED NOT NULL,
  `changed_by` bigint UNSIGNED NOT NULL,
  `changed_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_config_version`(`config_key` ASC, `version` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sys_login_log
-- ----------------------------
DROP TABLE IF EXISTS `sys_login_log`;
CREATE TABLE `sys_login_log`  (
  `id` bigint UNSIGNED NOT NULL,
  `principal_type` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `principal_id` bigint UNSIGNED NULL DEFAULT NULL,
  `login_method` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `success` tinyint(1) NOT NULL,
  `fail_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `ip_hash` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `device_hash` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_principal_time`(`principal_id` ASC, `created_at` DESC) USING BTREE,
  INDEX `idx_created_at`(`created_at` DESC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sys_operation_log
-- ----------------------------
DROP TABLE IF EXISTS `sys_operation_log`;
CREATE TABLE `sys_operation_log`  (
  `id` bigint UNSIGNED NOT NULL,
  `trace_id` char(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `actor_id` bigint UNSIGNED NOT NULL,
  `actor_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `module` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `action` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `target_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `target_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `detail` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `ip` varchar(45) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `request_method` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '',
  `request_path` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '',
  `request_params` json NULL,
  `status` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'SUCCESS',
  `error_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `duration_ms` int UNSIGNED NOT NULL DEFAULT 0,
  `created_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_actor_time`(`actor_id` ASC, `created_at` DESC) USING BTREE,
  INDEX `idx_module_time`(`module` ASC, `created_at` DESC) USING BTREE,
  INDEX `idx_target`(`target_type` ASC, `target_id` ASC) USING BTREE,
  INDEX `idx_created_at`(`created_at` DESC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sys_permission
-- ----------------------------
DROP TABLE IF EXISTS `sys_permission`;
CREATE TABLE `sys_permission`  (
  `id` bigint UNSIGNED NOT NULL,
  `permission_code` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `permission_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `resource_type` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `resource_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `parent_id` bigint UNSIGNED NOT NULL DEFAULT 0,
  `sort` int NOT NULL DEFAULT 0,
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_permission_code`(`permission_code` ASC) USING BTREE,
  INDEX `idx_parent_sort`(`parent_id` ASC, `sort` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sys_report_reason
-- ----------------------------
DROP TABLE IF EXISTS `sys_report_reason`;
CREATE TABLE `sys_report_reason`  (
  `id` bigint UNSIGNED NOT NULL,
  `reason_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `reason_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `target_types` json NOT NULL,
  `sort` int NOT NULL DEFAULT 0,
  `status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'ENABLED',
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_reason_code`(`reason_code` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sys_role
-- ----------------------------
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role`  (
  `id` bigint UNSIGNED NOT NULL,
  `role_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `role_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'NORMAL',
  `is_system` tinyint(1) NOT NULL DEFAULT 0,
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_role_code`(`role_code` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sys_role_permission
-- ----------------------------
DROP TABLE IF EXISTS `sys_role_permission`;
CREATE TABLE `sys_role_permission`  (
  `role_id` bigint UNSIGNED NOT NULL,
  `permission_id` bigint UNSIGNED NOT NULL,
  `created_at` datetime(3) NOT NULL,
  PRIMARY KEY (`role_id`, `permission_id`) USING BTREE,
  INDEX `fk_rp_permission`(`permission_id` ASC) USING BTREE,
  CONSTRAINT `fk_rp_permission` FOREIGN KEY (`permission_id`) REFERENCES `sys_permission` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_rp_role` FOREIGN KEY (`role_id`) REFERENCES `sys_role` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sys_sensitive_word
-- ----------------------------
DROP TABLE IF EXISTS `sys_sensitive_word`;
CREATE TABLE `sys_sensitive_word`  (
  `id` bigint UNSIGNED NOT NULL,
  `word` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `match_type` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `action` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'ENABLED',
  `created_by` bigint UNSIGNED NOT NULL,
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_sensitive_word`(`word` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sys_user
-- ----------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user`  (
  `id` bigint UNSIGNED NOT NULL,
  `phone_ciphertext` varbinary(512) NULL DEFAULT NULL,
  `phone_hmac` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `nickname` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `avatar_media_id` bigint UNSIGNED NULL DEFAULT NULL,
  `bio` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'NORMAL',
  `mute_until` datetime(3) NULL DEFAULT NULL,
  `suspend_until` datetime(3) NULL DEFAULT NULL,
  `violation_count` int UNSIGNED NOT NULL DEFAULT 0,
  `like_received_count` bigint UNSIGNED NOT NULL DEFAULT 0,
  `following_count` bigint UNSIGNED NOT NULL DEFAULT 0,
  `follower_count` bigint UNSIGNED NOT NULL DEFAULT 0,
  `last_login_at` datetime(3) NULL DEFAULT NULL,
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  `deleted_at` datetime(3) NULL DEFAULT NULL,
  `deleted_by` bigint UNSIGNED NULL DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_phone_hmac`(`phone_hmac` ASC) USING BTREE,
  INDEX `idx_status_created`(`status` ASC, `created_at` DESC) USING BTREE,
  INDEX `idx_nickname`(`nickname` ASC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sys_user_agreement
-- ----------------------------
DROP TABLE IF EXISTS `sys_user_agreement`;
CREATE TABLE `sys_user_agreement`  (
  `id` bigint UNSIGNED NOT NULL,
  `user_id` bigint UNSIGNED NOT NULL,
  `agreement_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `version` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `agreed_at` datetime(3) NOT NULL,
  `ip_hash` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_agreement`(`user_id` ASC, `agreement_type` ASC, `version` ASC) USING BTREE,
  CONSTRAINT `fk_agreement_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sys_user_wecom_identity
-- ----------------------------
DROP TABLE IF EXISTS `sys_user_wecom_identity`;
CREATE TABLE `sys_user_wecom_identity`  (
  `id` bigint UNSIGNED NOT NULL,
  `user_id` bigint UNSIGNED NOT NULL,
  `corp_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `wecom_user_id_ciphertext` varbinary(512) NOT NULL,
  `wecom_user_id_hmac` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `display_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `bind_status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'ACTIVE',
  `bound_at` datetime(3) NOT NULL,
  `unbound_at` datetime(3) NULL DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_corp_member`(`corp_id` ASC, `wecom_user_id_hmac` ASC) USING BTREE,
  INDEX `idx_user_status`(`user_id` ASC, `bind_status` ASC) USING BTREE,
  CONSTRAINT `fk_wecom_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sys_verification_record
-- ----------------------------
DROP TABLE IF EXISTS `sys_verification_record`;
CREATE TABLE `sys_verification_record`  (
  `id` bigint UNSIGNED NOT NULL,
  `target_hmac` char(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `purpose` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `expires_at` datetime(3) NOT NULL,
  `created_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_target_purpose`(`target_hmac` ASC, `purpose` ASC, `created_at` DESC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for user_comment_like
-- ----------------------------
DROP TABLE IF EXISTS `user_comment_like`;
CREATE TABLE `user_comment_like`  (
  `user_id` bigint UNSIGNED NOT NULL,
  `comment_id` bigint UNSIGNED NOT NULL,
  `created_at` datetime(3) NOT NULL,
  PRIMARY KEY (`user_id`, `comment_id`) USING BTREE,
  INDEX `idx_comment_like`(`comment_id` ASC) USING BTREE,
  CONSTRAINT `fk_comment_like_comment` FOREIGN KEY (`comment_id`) REFERENCES `video_comment` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_comment_like_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for user_follow
-- ----------------------------
DROP TABLE IF EXISTS `user_follow`;
CREATE TABLE `user_follow`  (
  `follower_id` bigint UNSIGNED NOT NULL,
  `followee_id` bigint UNSIGNED NOT NULL,
  `created_at` datetime(3) NOT NULL,
  PRIMARY KEY (`follower_id`, `followee_id`) USING BTREE,
  INDEX `idx_followee`(`followee_id` ASC, `created_at` DESC) USING BTREE,
  CONSTRAINT `fk_follow_followee` FOREIGN KEY (`followee_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_follow_follower` FOREIGN KEY (`follower_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for user_penalty
-- ----------------------------
DROP TABLE IF EXISTS `user_penalty`;
CREATE TABLE `user_penalty`  (
  `id` bigint UNSIGNED NOT NULL,
  `user_id` bigint UNSIGNED NOT NULL,
  `penalty_type` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `reason` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `source_type` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `source_id` bigint UNSIGNED NULL DEFAULT NULL,
  `starts_at` datetime(3) NOT NULL,
  `ends_at` datetime(3) NULL DEFAULT NULL,
  `status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'ACTIVE',
  `issued_by` bigint UNSIGNED NOT NULL,
  `revoked_by` bigint UNSIGNED NULL DEFAULT NULL,
  `revoke_reason` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_active`(`user_id` ASC, `status` ASC, `ends_at` ASC) USING BTREE,
  INDEX `idx_penalty_expiry`(`status` ASC, `ends_at` ASC) USING BTREE,
  INDEX `fk_penalty_issuer`(`issued_by` ASC) USING BTREE,
  CONSTRAINT `fk_penalty_issuer` FOREIGN KEY (`issued_by`) REFERENCES `sys_admin_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_penalty_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for user_video_favorite
-- ----------------------------
DROP TABLE IF EXISTS `user_video_favorite`;
CREATE TABLE `user_video_favorite`  (
  `user_id` bigint UNSIGNED NOT NULL,
  `video_id` bigint UNSIGNED NOT NULL,
  `created_at` datetime(3) NOT NULL,
  PRIMARY KEY (`user_id`, `video_id`) USING BTREE,
  INDEX `idx_favorite_video`(`video_id` ASC) USING BTREE,
  CONSTRAINT `fk_favorite_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_favorite_video` FOREIGN KEY (`video_id`) REFERENCES `video` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for user_video_feedback
-- ----------------------------
DROP TABLE IF EXISTS `user_video_feedback`;
CREATE TABLE `user_video_feedback`  (
  `user_id` bigint UNSIGNED NOT NULL,
  `video_id` bigint UNSIGNED NOT NULL,
  `feedback_type` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `created_at` datetime(3) NOT NULL,
  PRIMARY KEY (`user_id`, `video_id`) USING BTREE,
  INDEX `fk_feedback_video`(`video_id` ASC) USING BTREE,
  CONSTRAINT `fk_feedback_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_feedback_video` FOREIGN KEY (`video_id`) REFERENCES `video` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for user_video_history
-- ----------------------------
DROP TABLE IF EXISTS `user_video_history`;
CREATE TABLE `user_video_history`  (
  `user_id` bigint UNSIGNED NOT NULL,
  `video_id` bigint UNSIGNED NOT NULL,
  `last_viewed_at` datetime(3) NOT NULL,
  `view_count` int UNSIGNED NOT NULL DEFAULT 1,
  `last_progress_ms` int UNSIGNED NOT NULL DEFAULT 0,
  PRIMARY KEY (`user_id`, `video_id`) USING BTREE,
  INDEX `idx_user_recent`(`user_id` ASC, `last_viewed_at` DESC) USING BTREE,
  INDEX `fk_history_video`(`video_id` ASC) USING BTREE,
  CONSTRAINT `fk_history_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_history_video` FOREIGN KEY (`video_id`) REFERENCES `video` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for user_video_like
-- ----------------------------
DROP TABLE IF EXISTS `user_video_like`;
CREATE TABLE `user_video_like`  (
  `user_id` bigint UNSIGNED NOT NULL,
  `video_id` bigint UNSIGNED NOT NULL,
  `created_at` datetime(3) NOT NULL,
  PRIMARY KEY (`user_id`, `video_id`) USING BTREE,
  INDEX `idx_like_video`(`video_id` ASC) USING BTREE,
  CONSTRAINT `fk_like_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_like_video` FOREIGN KEY (`video_id`) REFERENCES `video` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for video
-- ----------------------------
DROP TABLE IF EXISTS `video`;
CREATE TABLE `video`  (
  `id` bigint UNSIGNED NOT NULL,
  `author_id` bigint UNSIGNED NOT NULL,
  `title` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `description` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `source_note` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `cover_media_id` bigint UNSIGNED NULL DEFAULT NULL,
  `original_media_id` bigint UNSIGNED NOT NULL,
  `audit_status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'DRAFT',
  `publish_status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'UNPUBLISHED',
  `visibility` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'PUBLIC',
  `risk_level` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'NONE',
  `manual_weight` int NOT NULL DEFAULT 0,
  `scheduled_at` datetime(3) NULL DEFAULT NULL,
  `published_at` datetime(3) NULL DEFAULT NULL,
  `off_shelf_at` datetime(3) NULL DEFAULT NULL,
  `off_shelf_reason` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `current_version` int UNSIGNED NOT NULL DEFAULT 1,
  `deleted` tinyint(1) NOT NULL DEFAULT 0,
  `deleted_at` datetime(3) NULL DEFAULT NULL,
  `deleted_by` bigint UNSIGNED NULL DEFAULT NULL,
  `created_by` bigint UNSIGNED NOT NULL,
  `updated_by` bigint UNSIGNED NOT NULL,
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_feed`(`publish_status` ASC, `audit_status` ASC, `visibility` ASC, `published_at` DESC) USING BTREE,
  INDEX `idx_author_status`(`author_id` ASC, `publish_status` ASC, `published_at` DESC) USING BTREE,
  INDEX `idx_audit_queue`(`audit_status` ASC, `risk_level` DESC, `updated_at` ASC) USING BTREE,
  INDEX `idx_schedule`(`publish_status` ASC, `scheduled_at` ASC) USING BTREE,
  INDEX `fk_video_cover`(`cover_media_id` ASC) USING BTREE,
  INDEX `fk_video_original`(`original_media_id` ASC) USING BTREE,
  CONSTRAINT `fk_video_author` FOREIGN KEY (`author_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_video_cover` FOREIGN KEY (`cover_media_id`) REFERENCES `media_resource` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_video_original` FOREIGN KEY (`original_media_id`) REFERENCES `media_resource` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for video_audit_record
-- ----------------------------
DROP TABLE IF EXISTS `video_audit_record`;
CREATE TABLE `video_audit_record`  (
  `id` bigint UNSIGNED NOT NULL,
  `video_id` bigint UNSIGNED NOT NULL,
  `video_version` int UNSIGNED NOT NULL,
  `reviewer_admin_id` bigint UNSIGNED NULL DEFAULT NULL,
  `action` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `reason` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `machine_risk_snapshot` json NULL,
  `claimed_at` datetime(3) NULL DEFAULT NULL,
  `reviewed_at` datetime(3) NULL DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_audit_video`(`video_id` ASC, `created_at` DESC) USING BTREE,
  INDEX `fk_audit_reviewer`(`reviewer_admin_id` ASC) USING BTREE,
  CONSTRAINT `fk_audit_reviewer` FOREIGN KEY (`reviewer_admin_id`) REFERENCES `sys_admin_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_audit_video` FOREIGN KEY (`video_id`) REFERENCES `video` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for video_category_map
-- ----------------------------
DROP TABLE IF EXISTS `video_category_map`;
CREATE TABLE `video_category_map`  (
  `video_id` bigint UNSIGNED NOT NULL,
  `category_id` bigint UNSIGNED NOT NULL,
  PRIMARY KEY (`video_id`, `category_id`) USING BTREE,
  INDEX `fk_vcm_category`(`category_id` ASC) USING BTREE,
  CONSTRAINT `fk_vcm_category` FOREIGN KEY (`category_id`) REFERENCES `content_category` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_vcm_video` FOREIGN KEY (`video_id`) REFERENCES `video` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for video_comment
-- ----------------------------
DROP TABLE IF EXISTS `video_comment`;
CREATE TABLE `video_comment`  (
  `id` bigint UNSIGNED NOT NULL,
  `video_id` bigint UNSIGNED NOT NULL,
  `user_id` bigint UNSIGNED NOT NULL,
  `root_comment_id` bigint UNSIGNED NOT NULL DEFAULT 0,
  `reply_to_comment_id` bigint UNSIGNED NULL DEFAULT NULL,
  `content` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'PENDING',
  `risk_level` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'NONE',
  `like_count` bigint UNSIGNED NOT NULL DEFAULT 0,
  `reply_count` bigint UNSIGNED NOT NULL DEFAULT 0,
  `hot_score` decimal(20, 6) NOT NULL DEFAULT 0.000000,
  `deleted_at` datetime(3) NULL DEFAULT NULL,
  `deleted_by` bigint UNSIGNED NULL DEFAULT NULL,
  `delete_reason` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_video_root_hot`(`video_id` ASC, `root_comment_id` ASC, `status` ASC, `hot_score` DESC, `id` DESC) USING BTREE,
  INDEX `idx_root_created`(`root_comment_id` ASC, `status` ASC, `created_at` ASC) USING BTREE,
  INDEX `idx_user_created`(`user_id` ASC, `created_at` DESC) USING BTREE,
  CONSTRAINT `fk_comment_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_comment_video` FOREIGN KEY (`video_id`) REFERENCES `video` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for video_publish_schedule
-- ----------------------------
DROP TABLE IF EXISTS `video_publish_schedule`;
CREATE TABLE `video_publish_schedule`  (
  `id` bigint UNSIGNED NOT NULL,
  `video_id` bigint UNSIGNED NOT NULL,
  `scheduled_at` datetime(3) NOT NULL,
  `status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'PENDING',
  `processing_started_at` datetime(3) NULL DEFAULT NULL,
  `executed_at` datetime(3) NULL DEFAULT NULL,
  `failure_reason` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `created_by` bigint UNSIGNED NOT NULL,
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_pending`(`status` ASC, `scheduled_at` ASC) USING BTREE,
  INDEX `fk_schedule_video`(`video_id` ASC) USING BTREE,
  INDEX `idx_publish_schedule_processing`(`status` ASC, `processing_started_at` ASC) USING BTREE,
  CONSTRAINT `fk_schedule_video` FOREIGN KEY (`video_id`) REFERENCES `video` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for video_share_event
-- ----------------------------
DROP TABLE IF EXISTS `video_share_event`;
CREATE TABLE `video_share_event`  (
  `id` bigint UNSIGNED NOT NULL,
  `video_id` bigint UNSIGNED NOT NULL,
  `user_id` bigint UNSIGNED NULL DEFAULT NULL,
  `channel` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `event_type` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `trace_id` char(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `created_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_share_event_video`(`video_id` ASC, `created_at` DESC) USING BTREE,
  INDEX `idx_share_event_user`(`user_id` ASC, `created_at` DESC) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for video_share_inbox
-- ----------------------------
DROP TABLE IF EXISTS `video_share_inbox`;
CREATE TABLE `video_share_inbox`  (
  `id` bigint UNSIGNED NOT NULL,
  `batch_id` bigint UNSIGNED NOT NULL,
  `sender_id` bigint UNSIGNED NOT NULL,
  `recipient_id` bigint UNSIGNED NOT NULL,
  `video_id` bigint UNSIGNED NOT NULL,
  `status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'DELIVERED',
  `invalid_reason` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `sent_at` datetime(3) NOT NULL,
  `opened_at` datetime(3) NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_recipient`(`recipient_id` ASC, `status` ASC, `sent_at` DESC) USING BTREE,
  INDEX `idx_share_video`(`video_id` ASC, `status` ASC) USING BTREE,
  INDEX `idx_sender_recent`(`sender_id` ASC, `sent_at` DESC) USING BTREE,
  CONSTRAINT `fk_share_recipient` FOREIGN KEY (`recipient_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_share_sender` FOREIGN KEY (`sender_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_share_video` FOREIGN KEY (`video_id`) REFERENCES `video` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for video_stats
-- ----------------------------
DROP TABLE IF EXISTS `video_stats`;
CREATE TABLE `video_stats`  (
  `video_id` bigint UNSIGNED NOT NULL,
  `exposure_count` bigint UNSIGNED NOT NULL DEFAULT 0,
  `play_count` bigint UNSIGNED NOT NULL DEFAULT 0,
  `effective_play_count` bigint UNSIGNED NOT NULL DEFAULT 0,
  `complete_play_count` bigint UNSIGNED NOT NULL DEFAULT 0,
  `play_duration_ms` bigint UNSIGNED NOT NULL DEFAULT 0,
  `like_count` bigint UNSIGNED NOT NULL DEFAULT 0,
  `favorite_count` bigint UNSIGNED NOT NULL DEFAULT 0,
  `comment_count` bigint UNSIGNED NOT NULL DEFAULT 0,
  `share_count` bigint UNSIGNED NOT NULL DEFAULT 0,
  `report_count` bigint UNSIGNED NOT NULL DEFAULT 0,
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`video_id`) USING BTREE,
  CONSTRAINT `fk_stats_video` FOREIGN KEY (`video_id`) REFERENCES `video` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for video_tag_map
-- ----------------------------
DROP TABLE IF EXISTS `video_tag_map`;
CREATE TABLE `video_tag_map`  (
  `video_id` bigint UNSIGNED NOT NULL,
  `tag_id` bigint UNSIGNED NOT NULL,
  PRIMARY KEY (`video_id`, `tag_id`) USING BTREE,
  INDEX `idx_tag_video`(`tag_id` ASC, `video_id` ASC) USING BTREE,
  CONSTRAINT `fk_vtm_tag` FOREIGN KEY (`tag_id`) REFERENCES `content_tag` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_vtm_video` FOREIGN KEY (`video_id`) REFERENCES `video` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for video_transcode
-- ----------------------------
DROP TABLE IF EXISTS `video_transcode`;
CREATE TABLE `video_transcode`  (
  `id` bigint UNSIGNED NOT NULL,
  `video_id` bigint UNSIGNED NOT NULL,
  `media_id` bigint UNSIGNED NULL DEFAULT NULL,
  `definition` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `status` varchar(24) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'PENDING',
  `error_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `error_message` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `started_at` datetime(3) NULL DEFAULT NULL,
  `finished_at` datetime(3) NULL DEFAULT NULL,
  `retry_count` int UNSIGNED NOT NULL DEFAULT 0,
  `created_at` datetime(3) NOT NULL,
  `updated_at` datetime(3) NOT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_video_definition`(`video_id` ASC, `definition` ASC) USING BTREE,
  INDEX `idx_transcode_status`(`status` ASC, `created_at` ASC) USING BTREE,
  INDEX `fk_transcode_media`(`media_id` ASC) USING BTREE,
  CONSTRAINT `fk_transcode_media` FOREIGN KEY (`media_id`) REFERENCES `media_resource` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT `fk_transcode_video` FOREIGN KEY (`video_id`) REFERENCES `video` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
