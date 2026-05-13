-- V9: Add messages_today, active_rooms, and storage_mb columns to analytics_snapshots
-- These three columns complete the platform analytics requirements from the use case spec:
--   messages_today  — count of non-deleted messages sent since midnight UTC (daily volume)
--   active_rooms    — count of rooms with lastMessageAt in the last 24 hours
--   storage_mb      — total file storage used across all media uploads (in megabytes)

ALTER TABLE analytics_snapshots
    ADD COLUMN messages_today BIGINT NOT NULL DEFAULT 0 AFTER suspended_users,
    ADD COLUMN active_rooms   BIGINT NOT NULL DEFAULT 0 AFTER messages_today,
    ADD COLUMN storage_mb     DOUBLE NOT NULL DEFAULT 0 AFTER active_rooms;
