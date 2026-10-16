-- The test-only upload screen and endpoint have been removed. Keep its shared
-- upload permission for the real OSS signing and relay endpoints.
UPDATE sys_permission
SET permission_name = '媒体文件上传',
    resource_path = '/api/v1/media/oss/upload',
    updated_at = UTC_TIMESTAMP(3)
WHERE permission_code = 'oss-test:upload';
