ALTER TABLE sys_user
  ADD COLUMN password_hash VARCHAR(255) NULL AFTER phone_hmac;
