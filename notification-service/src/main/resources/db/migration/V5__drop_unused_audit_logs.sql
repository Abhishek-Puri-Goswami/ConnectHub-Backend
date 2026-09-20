-- notification-service never received audit events (nothing produced them); admin audit logs live in auth-service.
DROP TABLE IF EXISTS audit_logs;
