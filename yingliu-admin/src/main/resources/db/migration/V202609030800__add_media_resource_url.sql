ALTER TABLE media_resource
    ADD COLUMN url VARCHAR(1024) NULL AFTER relative_path;
