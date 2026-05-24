-- V10: Create announcements table for platform-wide admin announcements
-- Admins can post messages that appear in every user's sidebar Announcements section.

CREATE TABLE IF NOT EXISTS announcements (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    title       VARCHAR(200),
    content     VARCHAR(2000) NOT NULL,
    admin_id    INT,
    admin_name  VARCHAR(100),
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
