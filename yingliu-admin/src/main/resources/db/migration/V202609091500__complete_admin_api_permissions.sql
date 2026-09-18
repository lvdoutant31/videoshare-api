-- Complete the B-side API permission catalogue and built-in role grants.
-- Safe to run once through Flyway or manually on an existing database.

INSERT IGNORE INTO sys_permission
    (id, permission_code, permission_name, resource_type, resource_path, parent_id, sort, created_at, updated_at)
VALUES
    (301, 'dashboard:view',       '查看工作台',       'API', '/api/v1/admin/dashboard/*',              0, 301, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (302, 'storage:view',         '查看存储状态',     'API', '/api/v1/admin/dashboard/storage',        0, 302, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (310, 'video:view',           '查看视频',         'API', '/api/v1/admin/videos',                   0, 310, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (311, 'video:create',         '创建视频',         'API', '/api/v1/admin/videos',                   0, 311, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (312, 'video:update',         '编辑视频',         'API', '/api/v1/admin/videos/*',                 0, 312, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (313, 'video:submit-review',  '提交视频审核',     'API', '/api/v1/admin/videos/*/submit-review',   0, 313, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (314, 'video:publish',        '发布视频',         'API', '/api/v1/admin/videos/*/publish',         0, 314, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (315, 'video:off-shelf',      '上下架视频',       'API', '/api/v1/admin/videos/*/off-shelf',       0, 315, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (316, 'video:delete',         '删除视频',         'API', '/api/v1/admin/videos/*',                 0, 316, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (317, 'video:transcode:retry','重试视频转码',     'API', '/api/v1/admin/videos/*/transcodes/*',    0, 317, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (320, 'review:view',          '查看审核任务',     'API', '/api/v1/admin/reviews/videos',           0, 320, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (321, 'review:claim',         '领取审核任务',     'API', '/api/v1/admin/reviews/videos/*/claim',   0, 321, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (322, 'review:decide',        '处理审核任务',     'API', '/api/v1/admin/reviews/videos/*',         0, 322, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (330, 'category:view',        '查看分类',         'API', '/api/v1/admin/categories',               0, 330, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (331, 'category:manage',      '管理分类',         'API', '/api/v1/admin/categories/*',             0, 331, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (332, 'tag:view',             '查看标签',         'API', '/api/v1/admin/tags',                     0, 332, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (333, 'tag:manage',           '管理标签',         'API', '/api/v1/admin/tags/*',                   0, 333, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (340, 'comment:view',         '查看评论',         'API', '/api/v1/admin/comments',                 0, 340, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (341, 'comment:hide',         '隐藏评论',         'API', '/api/v1/admin/comments/*/hide',          0, 341, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (342, 'comment:restore',      '恢复评论',         'API', '/api/v1/admin/comments/*/restore',       0, 342, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (343, 'comment:delete',       '删除评论',         'API', '/api/v1/admin/comments/*',               0, 343, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (344, 'comment:moderate',     '处置评论',         'API', '/api/v1/admin/moderation/comments/*',    0, 344, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (350, 'report:view',          '查看举报',         'API', '/api/v1/admin/reports',                  0, 350, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (351, 'report:claim',         '领取举报',         'API', '/api/v1/admin/reports/*/claim',          0, 351, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (352, 'report:resolve',       '解决举报',         'API', '/api/v1/admin/reports/*/resolve',        0, 352, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (353, 'report:reject',        '驳回举报',         'API', '/api/v1/admin/reports/*/reject',         0, 353, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (360, 'operation-log:view',   '查看操作日志',     'API', '/api/v1/admin/operation-logs',           0, 360, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)),
    (370, 'oss-test:upload',      '使用 OSS 上传测试','API', '/api/v1/admin/test/oss/upload',          0, 370, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));

-- Super administrators receive every permission currently registered.
INSERT IGNORE INTO sys_role_permission (role_id, permission_id, created_at)
SELECT r.id, p.id, UTC_TIMESTAMP(3)
FROM sys_role r
JOIN sys_permission p
WHERE r.role_code = 'SUPER_ADMIN';

-- Content operators manage content and routine moderation.
INSERT IGNORE INTO sys_role_permission (role_id, permission_id, created_at)
SELECT r.id, p.id, UTC_TIMESTAMP(3)
FROM sys_role r
JOIN sys_permission p ON p.permission_code IN (
    'dashboard:view', 'storage:view',
    'video:view', 'video:create', 'video:update', 'video:submit-review', 'video:publish',
    'video:off-shelf', 'video:delete', 'video:transcode:retry',
    'category:view', 'category:manage', 'tag:view', 'tag:manage',
    'comment:view', 'comment:hide', 'comment:restore', 'comment:delete', 'comment:moderate',
    'report:view', 'report:claim', 'report:resolve', 'report:reject'
)
WHERE r.role_code = 'OPERATOR';

-- Reviewers can inspect content and make review decisions.
INSERT IGNORE INTO sys_role_permission (role_id, permission_id, created_at)
SELECT r.id, p.id, UTC_TIMESTAMP(3)
FROM sys_role r
JOIN sys_permission p ON p.permission_code IN (
    'dashboard:view', 'storage:view', 'video:view',
    'review:view', 'review:claim', 'review:decide',
    'comment:view', 'comment:hide', 'comment:restore',
    'report:view', 'report:claim', 'report:resolve', 'report:reject'
)
WHERE r.role_code = 'REVIEWER';

-- Customer service can inspect users and handle comments and reports.
INSERT IGNORE INTO sys_role_permission (role_id, permission_id, created_at)
SELECT r.id, p.id, UTC_TIMESTAMP(3)
FROM sys_role r
JOIN sys_permission p ON p.permission_code IN (
    'dashboard:view', 'storage:view',
    'user:view', 'user:mute', 'user:suspend', 'user:penalty:revoke', 'user:wecom:unbind',
    'comment:view', 'comment:hide', 'comment:restore',
    'report:view', 'report:claim', 'report:resolve', 'report:reject'
)
WHERE r.role_code = 'CUSTOMER_SERVICE';

