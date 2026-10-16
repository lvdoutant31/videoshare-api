-- 管理员账号管理权限
INSERT INTO sys_permission (id, permission_code, permission_name, resource_type, resource_path, parent_id, sort, created_at, updated_at) VALUES
  (201, 'admin:view',    '查看管理员',   'API', '/api/v1/admin/admins',            0, 201, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
  (202, 'admin:create',  '创建管理员',   'API', '/api/v1/admin/admins',            0, 202, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
  (203, 'admin:update',  '编辑管理员',   'API', '/api/v1/admin/admins/*',          0, 203, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
  (204, 'admin:password', '重置密码',    'API', '/api/v1/admin/admins/*/password',  0, 204, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
  (205, 'admin:status',  '启停管理员',   'API', '/api/v1/admin/admins/*/status',    0, 205, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

-- 超级管理员拥有全部管理员管理权限
INSERT INTO sys_role_permission (role_id, permission_id, created_at)
SELECT 1, id, UTC_TIMESTAMP(3) FROM sys_permission WHERE id BETWEEN 201 AND 205;
