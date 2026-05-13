-- User email notification preferences: opt-in/opt-out for missed-message email digests.
-- Each user has at most one row; upsert on userId.
CREATE TABLE user_email_preferences (
    user_id                    INT          NOT NULL,
    email                      VARCHAR(100) NOT NULL,
    email_notifications_enabled BOOLEAN      NOT NULL DEFAULT TRUE,
    updated_at                 DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (user_id)
);

-- Track whether a notification has already been included in an email digest so we
-- never send the same notification twice even if the scheduler runs multiple times.
ALTER TABLE notifications ADD COLUMN email_sent BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE notifications ADD INDEX idx_notif_email_digest (recipient_id, is_read, email_sent, created_at);
