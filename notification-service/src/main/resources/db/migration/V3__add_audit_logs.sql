CREATE TABLE audit_logs (
    audit_id    BIGINT       NOT NULL AUTO_INCREMENT,
    actor_id    INT          NOT NULL,
    action      VARCHAR(50)  NOT NULL,
    entity_type VARCHAR(50)  NULL,
    entity_id   VARCHAR(36)  NULL,
    details     VARCHAR(500) NULL,
    ip_address  VARCHAR(45)  NULL,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (audit_id),
    INDEX idx_audit_actor (actor_id),
    INDEX idx_audit_created (created_at)
);
