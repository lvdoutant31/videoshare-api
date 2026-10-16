ALTER TABLE sys_operation_log ADD COLUMN request_method VARCHAR(8) NOT NULL DEFAULT '' AFTER ip;
ALTER TABLE sys_operation_log ADD COLUMN request_path VARCHAR(512) NOT NULL DEFAULT '' AFTER request_method;
ALTER TABLE sys_operation_log ADD COLUMN request_params JSON NULL AFTER request_path;
