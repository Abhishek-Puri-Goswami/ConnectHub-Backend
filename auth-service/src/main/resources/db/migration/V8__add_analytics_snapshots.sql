CREATE TABLE analytics_snapshots (
    snapshot_id     BIGINT      NOT NULL AUTO_INCREMENT,
    snapshot_at     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    online_count    INT         NOT NULL DEFAULT 0,
    total_users     BIGINT      NOT NULL DEFAULT 0,
    active_users    BIGINT      NOT NULL DEFAULT 0,
    suspended_users BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (snapshot_id),
    INDEX idx_analytics_snapshot_at (snapshot_at)
);
