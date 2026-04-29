CREATE TABLE device_tokens (
  token_id    BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id     INT NOT NULL,
  fcm_token   VARCHAR(255) NOT NULL,
  platform    VARCHAR(20) NOT NULL,
  created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  UNIQUE KEY uq_user_fcm (user_id, fcm_token),
  INDEX idx_device_user (user_id)
);
