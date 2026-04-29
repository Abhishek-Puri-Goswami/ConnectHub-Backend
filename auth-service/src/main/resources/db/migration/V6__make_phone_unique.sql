-- V6: Enforce global phone number uniqueness.
-- Allows multiple NULL values, but non-null phone numbers must be unique.

SET @has_uq_users_phone_number := (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'users'
      AND index_name = 'uq_users_phone_number'
);

SET @sql_uq_users_phone_number := IF(
    @has_uq_users_phone_number = 0,
    'CREATE UNIQUE INDEX uq_users_phone_number ON users (phone_number)',
    'SELECT 1'
);

PREPARE stmt_uq_users_phone_number FROM @sql_uq_users_phone_number;
EXECUTE stmt_uq_users_phone_number;
DEALLOCATE PREPARE stmt_uq_users_phone_number;
