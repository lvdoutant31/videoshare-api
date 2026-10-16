ALTER TABLE video
  MODIFY description MEDIUMTEXT NULL COMMENT '富文本正文（兼容原 description 接口字段）',
  MODIFY original_media_id BIGINT UNSIGNED NULL;

-- Existing descriptions were plain text: escape markup and preserve line breaks.
UPDATE video SET description = CONCAT('<p>',
  REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(description,
    '&', '&amp;'), '<', '&lt;'), '>', '&gt;'), '\r\n', '\n'), '\r', '\n'), '\n', '<br>'), '</p>')
WHERE description IS NOT NULL AND description <> '';
