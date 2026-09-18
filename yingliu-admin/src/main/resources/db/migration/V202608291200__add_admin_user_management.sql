CREATE TABLE sys_admin_confirmation (
  id BIGINT UNSIGNED NOT NULL,
  token_hash CHAR(64) NOT NULL,
  actor_admin_id BIGINT UNSIGNED NOT NULL,
  action VARCHAR(64) NOT NULL,
  target_type VARCHAR(32) NOT NULL,
  target_id BIGINT UNSIGNED NOT NULL,
  expires_at DATETIME(3) NOT NULL,
  consumed_at DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_admin_confirmation_token_hash (token_hash),
  KEY idx_admin_confirmation_consume (actor_admin_id, action, target_type, target_id, expires_at, consumed_at),
  CONSTRAINT fk_admin_confirmation_actor FOREIGN KEY (actor_admin_id) REFERENCES sys_admin_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO sys_permission (id, permission_code, permission_name, resource_type, resource_path, parent_id, sort, created_at, updated_at) VALUES
  (101, 'user:view', '查看用户', 'API', '/api/v1/admin/users', 0, 101, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
  (102, 'user:mute', '禁言用户', 'API', '/api/v1/admin/users/*/mute', 0, 102, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
  (103, 'user:suspend', '临时封禁用户', 'API', '/api/v1/admin/users/*/suspend', 0, 103, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
  (104, 'user:ban', '永久封禁用户', 'API', '/api/v1/admin/users/*/ban', 0, 104, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
  (105, 'user:penalty:revoke', '撤销用户处罚', 'API', '/api/v1/admin/users/*/penalties/*/revoke', 0, 105, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
  (106, 'user:wecom:unbind', '解绑企业微信', 'API', '/api/v1/admin/users/*/wecom-binding/unbind', 0, 106, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

INSERT INTO sys_role_permission (role_id, permission_id, created_at)
SELECT 1, id, UTC_TIMESTAMP(3) FROM sys_permission WHERE id BETWEEN 101 AND 106;

INSERT INTO sys_role_permission (role_id, permission_id, created_at)
SELECT 2, id, UTC_TIMESTAMP(3) FROM sys_permission WHERE id IN (101, 102, 103, 104, 105, 106);
