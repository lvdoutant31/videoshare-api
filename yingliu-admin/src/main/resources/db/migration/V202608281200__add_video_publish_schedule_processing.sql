ALTER TABLE video_publish_schedule
    ADD COLUMN processing_started_at DATETIME(3) NULL AFTER status,
    ADD KEY idx_publish_schedule_processing (status, processing_started_at);
