CREATE TABLE alert_disposal_idempotency (
    school_id VARCHAR(64) NOT NULL,
    canteen_id VARCHAR(64) NOT NULL,
    warn_id VARCHAR(192) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    payload_hash VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (warn_id),
    CONSTRAINT uk_alert_disposal_scope_key
        UNIQUE (school_id, canteen_id, idempotency_key)
);
