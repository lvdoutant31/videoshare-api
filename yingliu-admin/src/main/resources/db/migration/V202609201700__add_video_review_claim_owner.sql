ALTER TABLE video
  ADD COLUMN review_claimed_by BIGINT UNSIGNED NULL AFTER manual_weight,
  ADD COLUMN review_claimed_at DATETIME(3) NULL AFTER review_claimed_by,
  ADD KEY idx_review_claim (audit_status, review_claimed_by, review_claimed_at);

UPDATE video
SET review_claimed_by = updated_by,
    review_claimed_at = updated_at
WHERE audit_status = 'CLAIMED';
