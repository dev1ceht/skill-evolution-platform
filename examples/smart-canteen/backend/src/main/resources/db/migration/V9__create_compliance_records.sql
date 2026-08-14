CREATE TABLE compliance_records (
    school_id VARCHAR(64) NOT NULL,
    canteen_id VARCHAR(64) NOT NULL,
    record_id VARCHAR(64) NOT NULL,
    category VARCHAR(40) NOT NULL,
    subject_type VARCHAR(32) NOT NULL,
    subject_id VARCHAR(64) NOT NULL,
    subject_name VARCHAR(200) NOT NULL,
    title VARCHAR(200) NOT NULL,
    credential_no VARCHAR(100),
    valid_from DATE NOT NULL,
    valid_to DATE NOT NULL,
    attachment_refs_json TEXT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    review_remark VARCHAR(1000),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    submitted_at TIMESTAMP NULL,
    reviewed_at TIMESTAMP NULL,
    reviewed_by VARCHAR(64),
    PRIMARY KEY (school_id, canteen_id, record_id),
    CONSTRAINT ck_compliance_category CHECK (category IN (
        'LICENSE', 'HEALTH_CERTIFICATE', 'MANAGEMENT_DOCUMENT',
        'SUPPLIER_QUALIFICATION', 'WASTE_RECYCLER_QUALIFICATION'
    )),
    CONSTRAINT ck_compliance_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_compliance_dates CHECK (valid_to >= valid_from),
    CONSTRAINT fk_compliance_school FOREIGN KEY (school_id) REFERENCES schools(id),
    CONSTRAINT fk_compliance_canteen FOREIGN KEY (canteen_id) REFERENCES canteens(id)
);

CREATE INDEX idx_compliance_scope_status_expiry
    ON compliance_records(school_id, canteen_id, status, valid_to);

CREATE TABLE compliance_record_history (
    school_id VARCHAR(64) NOT NULL,
    canteen_id VARCHAR(64) NOT NULL,
    history_id VARCHAR(64) NOT NULL,
    record_id VARCHAR(64) NOT NULL,
    action VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    snapshot_json TEXT NOT NULL,
    actor_id VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (school_id, canteen_id, history_id),
    CONSTRAINT fk_compliance_history_record FOREIGN KEY (school_id, canteen_id, record_id)
        REFERENCES compliance_records(school_id, canteen_id, record_id)
);

CREATE INDEX idx_compliance_history_record_time
    ON compliance_record_history(school_id, canteen_id, record_id, occurred_at);
