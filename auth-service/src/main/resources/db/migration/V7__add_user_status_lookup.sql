-- Lookup table for user-settable status values.
-- OFFLINE is intentionally excluded — it is a system-only state
-- set on WebSocket disconnect and cannot be chosen by the user.
CREATE TABLE user_status (
    id   TINYINT      NOT NULL AUTO_INCREMENT,
    name VARCHAR(20)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_user_status_name (name)
);

INSERT INTO user_status (name) VALUES
    ('ONLINE'),
    ('AWAY'),
    ('DND'),
    ('INVISIBLE');
