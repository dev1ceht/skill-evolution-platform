CREATE TABLE ledger_configurations (
    school_id VARCHAR(64) NOT NULL,
    canteen_id VARCHAR(64) NOT NULL,
    configuration_id VARCHAR(64) NOT NULL,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(200) NOT NULL,
    frequency VARCHAR(16) NOT NULL,
    period_days INTEGER NULL,
    required_fields_json TEXT NOT NULL,
    template_json TEXT NOT NULL,
    responsible_role VARCHAR(64),
    reminder_days INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (school_id, canteen_id, configuration_id),
    CONSTRAINT uk_ledger_config_scope_code UNIQUE (school_id, canteen_id, code),
    CONSTRAINT ck_ledger_config_frequency CHECK (frequency IN ('DAILY', 'WEEKLY', 'MONTHLY', 'CUSTOM')),
    CONSTRAINT ck_ledger_config_custom_period CHECK (
        (frequency = 'CUSTOM' AND period_days BETWEEN 1 AND 365)
        OR (frequency <> 'CUSTOM' AND period_days IS NULL)
    ),
    CONSTRAINT ck_ledger_config_reminder_days CHECK (reminder_days BETWEEN 0 AND 365),
    CONSTRAINT ck_ledger_config_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT fk_ledger_config_school FOREIGN KEY (school_id) REFERENCES schools(id),
    CONSTRAINT fk_ledger_config_canteen FOREIGN KEY (canteen_id) REFERENCES canteens(id)
);

CREATE INDEX idx_ledger_config_scope_status
    ON ledger_configurations(school_id, canteen_id, status, code);

ALTER TABLE ledger_cycles
    ADD configuration_id VARCHAR(64) NULL;

CREATE INDEX idx_ledger_cycles_configuration
    ON ledger_cycles(school_id, canteen_id, configuration_id, period_start, period_end);
