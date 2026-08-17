CREATE TABLE canteen_showcases (
    school_id VARCHAR(64) NOT NULL,
    canteen_id VARCHAR(64) NOT NULL,
    showcase_id VARCHAR(64) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content VARCHAR(5000) NOT NULL,
    photos_json TEXT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    previous_version_id VARCHAR(64),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    review_remark VARCHAR(1000),
    reviewed_at TIMESTAMP NULL,
    reviewed_by VARCHAR(64),
    published_at TIMESTAMP NULL,
    PRIMARY KEY (school_id, canteen_id, showcase_id),
    CONSTRAINT ck_showcase_status CHECK (status IN (
        'DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'PUBLISHED', 'REVOKED'
    )),
    CONSTRAINT fk_showcase_school FOREIGN KEY (school_id) REFERENCES schools(id),
    CONSTRAINT fk_showcase_canteen FOREIGN KEY (canteen_id) REFERENCES canteens(id)
);

CREATE INDEX idx_showcase_scope_status_time
    ON canteen_showcases(school_id, canteen_id, status, updated_at);

CREATE TABLE meal_suspensions (
    school_id VARCHAR(64) NOT NULL,
    canteen_id VARCHAR(64) NOT NULL,
    suspension_id VARCHAR(64) NOT NULL,
    meal_date DATE NOT NULL,
    meal_period VARCHAR(16) NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'SUBMITTED',
    review_remark VARCHAR(1000),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP NULL,
    reviewed_by VARCHAR(64),
    PRIMARY KEY (school_id, canteen_id, suspension_id),
    CONSTRAINT uk_suspension_scope_slot UNIQUE (school_id, canteen_id, meal_date, meal_period),
    CONSTRAINT ck_suspension_period CHECK (meal_period IN ('BREAKFAST', 'LUNCH', 'DINNER', 'SNACK')),
    CONSTRAINT ck_suspension_status CHECK (status IN ('SUBMITTED', 'APPROVED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT fk_suspension_school FOREIGN KEY (school_id) REFERENCES schools(id),
    CONSTRAINT fk_suspension_canteen FOREIGN KEY (canteen_id) REFERENCES canteens(id)
);

CREATE INDEX idx_suspension_scope_date_status
    ON meal_suspensions(school_id, canteen_id, meal_date, status);

CREATE TABLE supplier_complaints (
    school_id VARCHAR(64) NOT NULL,
    canteen_id VARCHAR(64) NOT NULL,
    complaint_id VARCHAR(64) NOT NULL,
    supplier_id VARCHAR(64) NOT NULL,
    subject VARCHAR(200) NOT NULL,
    description VARCHAR(5000) NOT NULL,
    attachment_refs_json TEXT NOT NULL,
    deadline DATE NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'SUBMITTED',
    reply VARCHAR(3000),
    version BIGINT NOT NULL DEFAULT 0,
    created_by VARCHAR(64) NOT NULL,
    assigned_to VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    accepted_at TIMESTAMP NULL,
    closed_at TIMESTAMP NULL,
    PRIMARY KEY (school_id, canteen_id, complaint_id),
    CONSTRAINT ck_complaint_status CHECK (status IN (
        'SUBMITTED', 'ACCEPTED', 'PROCESSING', 'REPLIED', 'CLOSED', 'REJECTED'
    )),
    CONSTRAINT fk_complaint_school FOREIGN KEY (school_id) REFERENCES schools(id),
    CONSTRAINT fk_complaint_canteen FOREIGN KEY (canteen_id) REFERENCES canteens(id)
);

CREATE INDEX idx_complaint_scope_status_time
    ON supplier_complaints(school_id, canteen_id, status, updated_at);

CREATE TABLE governance_history (
    school_id VARCHAR(64) NOT NULL,
    canteen_id VARCHAR(64) NOT NULL,
    history_id VARCHAR(64) NOT NULL,
    entity_type VARCHAR(32) NOT NULL,
    entity_id VARCHAR(64) NOT NULL,
    action VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    snapshot_json TEXT NOT NULL,
    actor_id VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (school_id, canteen_id, history_id),
    CONSTRAINT ck_governance_entity_type CHECK (
        entity_type IN ('SHOWCASE', 'MEAL_SUSPENSION', 'SUPPLIER_COMPLAINT')
    )
);

CREATE INDEX idx_governance_history_entity_time
    ON governance_history(school_id, canteen_id, entity_type, entity_id, occurred_at);
