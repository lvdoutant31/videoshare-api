-- Separate runtime-log visibility and direct per-admin grants.
CREATE TABLE sys_admin_user_permission (
  admin_user_id BIGINT UNSIGNED NOT NULL,
  permission_id BIGINT UNSIGNED NOT NULL,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (admin_user_id, permission_id),
  CONSTRAINT fk_aup_admin FOREIGN KEY (admin_user_id) REFERENCES sys_admin_user(id),
  CONSTRAINT fk_aup_permission FOREIGN KEY (permission_id) REFERENCES sys_permission(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT IGNORE INTO sys_permission
  (id, permission_code, permission_name, resource_type, resource_path, parent_id, sort, created_at, updated_at)
VALUES
  (380, 'admin:permissions', '配置管理员权限', 'API', '/api/v1/admin/admins/*/permissions', 0, 380, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
  (381, 'runtime-log:view', '查看运行日志', 'API', '/api/v1/admin/runtime-logs', 0, 381, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
  (382, 'category:create', '新建分类', 'API', '/api/v1/admin/categories', 0, 382, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
  (383, 'category:update', '编辑分类', 'API', '/api/v1/admin/categories/*', 0, 383, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
  (384, 'category:delete', '删除分类', 'API', '/api/v1/admin/categories/*', 0, 384, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
  (385, 'category:restore', '恢复分类', 'API', '/api/v1/admin/categories/*/restore', 0, 385, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
  (386, 'tag:create', '新建标签', 'API', '/api/v1/admin/tags', 0, 386, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
  (387, 'tag:update', '编辑标签', 'API', '/api/v1/admin/tags/*', 0, 387, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
  (388, 'tag:delete', '删除标签', 'API', '/api/v1/admin/tags/*', 0, 388, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
  (389, 'tag:restore', '恢复标签', 'API', '/api/v1/admin/tags/*/restore', 0, 389, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

-- Preserve each existing ordinary administrator's effective grants at cutover.
INSERT IGNORE INTO sys_admin_user_permission (admin_user_id, permission_id, created_at)
SELECT aur.admin_user_id, rp.permission_id, UTC_TIMESTAMP(3)
FROM sys_admin_user_role aur
JOIN sys_role r ON r.id = aur.role_id AND r.status = 'NORMAL' AND r.role_code <> 'SUPER_ADMIN'
JOIN sys_role_permission rp ON rp.role_id = r.id;

-- Split the former all-or-nothing taxonomy grant into its actual operations.
INSERT IGNORE INTO sys_admin_user_permission (admin_user_id, permission_id, created_at)
SELECT aup.admin_user_id, target.id, UTC_TIMESTAMP(3)
FROM sys_admin_user_permission aup
JOIN sys_permission old ON old.id = aup.permission_id AND old.permission_code = 'category:manage'
JOIN sys_permission target ON target.permission_code IN ('category:create', 'category:update', 'category:delete', 'category:restore');
INSERT IGNORE INTO sys_admin_user_permission (admin_user_id, permission_id, created_at)
SELECT aup.admin_user_id, target.id, UTC_TIMESTAMP(3)
FROM sys_admin_user_permission aup
JOIN sys_permission old ON old.id = aup.permission_id AND old.permission_code = 'tag:manage'
JOIN sys_permission target ON target.permission_code IN ('tag:create', 'tag:update', 'tag:delete', 'tag:restore');

-- Runtime logs used to share operation-log:view. Preserve that access once,
-- then keep the two pages independently assignable.
INSERT IGNORE INTO sys_admin_user_permission (admin_user_id, permission_id, created_at)
SELECT aur.admin_user_id, 381, UTC_TIMESTAMP(3)
FROM sys_admin_user_role aur
JOIN sys_role_permission rp ON rp.role_id = aur.role_id
JOIN sys_permission p ON p.id = rp.permission_id AND p.permission_code = 'operation-log:view';

INSERT IGNORE INTO sys_role_permission (role_id, permission_id, created_at)
SELECT r.id, 380, UTC_TIMESTAMP(3) FROM sys_role r WHERE r.role_code = 'SUPER_ADMIN';
INSERT IGNORE INTO sys_role_permission (role_id, permission_id, created_at)
SELECT r.id, 381, UTC_TIMESTAMP(3) FROM sys_role r WHERE r.role_code = 'SUPER_ADMIN';
INSERT IGNORE INTO sys_role_permission (role_id, permission_id, created_at)
SELECT r.id, p.id, UTC_TIMESTAMP(3)
FROM sys_role r JOIN sys_permission p ON p.permission_code IN (
  'category:create', 'category:update', 'category:delete', 'category:restore',
  'tag:create', 'tag:update', 'tag:delete', 'tag:restore'
) WHERE r.role_code = 'SUPER_ADMIN';

