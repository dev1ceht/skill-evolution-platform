CREATE TABLE alert_records (
    warn_id VARCHAR(192) NOT NULL,
    source VARCHAR(32) NOT NULL,
    third_warn_id VARCHAR(128) NOT NULL,
    school_id VARCHAR(64) NOT NULL,
    school_name VARCHAR(200),
    area_code VARCHAR(32),
    device_id VARCHAR(64),
    device_name VARCHAR(200),
    canteen_id VARCHAR(64),
    warn_happen_time TIMESTAMP NOT NULL,
    alarm_event_id VARCHAR(64) NOT NULL,
    warn_full_pic VARCHAR(500),
    warn_content VARCHAR(2000) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'UNPROCESSED',
    process_status INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    process_time TIMESTAMP NULL,
    process_user VARCHAR(128),
    process_content VARCHAR(2000),
    process_file VARCHAR(500),
    PRIMARY KEY (warn_id),
    CONSTRAINT uk_alert_source_third UNIQUE (source, third_warn_id),
    CONSTRAINT ck_alert_status CHECK (status IN ('UNPROCESSED', 'PROCESSED')),
    CONSTRAINT ck_alert_process_status CHECK (process_status IN (0, 1))
);

CREATE INDEX idx_alert_school_time
    ON alert_records(school_id, warn_happen_time, status);

CREATE INDEX idx_alert_source_time
    ON alert_records(source, warn_happen_time);
