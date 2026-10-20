-- Preserve existing reason names, status, ordering and target types.
INSERT INTO sys_report_reason (id, reason_code, reason_name, target_types, sort, status, created_at, updated_at)
SELECT defaults.id, defaults.code, defaults.name, JSON_ARRAY('VIDEO', 'COMMENT', 'USER'),
       defaults.sort, 'ENABLED', NOW(3), NOW(3)
FROM (
    SELECT 1800000000000000001 AS id, 'SPAM' AS code, '垃圾广告/营销' AS name, 10 AS sort
    UNION ALL SELECT 1800000000000000002, 'PORNOGRAPHY', '色情低俗', 20
    UNION ALL SELECT 1800000000000000003, 'VIOLENCE', '暴力血腥', 30
    UNION ALL SELECT 1800000000000000004, 'INFRINGEMENT', '侵犯权益', 40
    UNION ALL SELECT 1800000000000000005, 'RUMOR', '不实信息', 50
    UNION ALL SELECT 1800000000000000006, 'OTHER', '其他', 60
) AS defaults
WHERE NOT EXISTS (SELECT 1 FROM sys_report_reason existing WHERE existing.reason_code = defaults.code);
