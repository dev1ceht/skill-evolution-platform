CREATE TABLE agent_runs (
    run_id VARCHAR(64) PRIMARY KEY,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    actor_user_id VARCHAR(64) NOT NULL,
    actor_username VARCHAR(128) NOT NULL,
    school_id VARCHAR(64) NOT NULL,
    canteen_id VARCHAR(64) NOT NULL,
    intent VARCHAR(128) NOT NULL,
    skill_id VARCHAR(128) NOT NULL,
    skill_version VARCHAR(32) NOT NULL,
    manifest_digest CHAR(64) NOT NULL,
    plan_hash CHAR(64) NOT NULL,
    plan_json TEXT NOT NULL,
    input_json TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    current_step VARCHAR(128),
    result_json TEXT,
    error_code VARCHAR(64),
    error_message VARCHAR(2000),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_agent_run_idempotency
        UNIQUE (actor_user_id, school_id, canteen_id, idempotency_key),
    CONSTRAINT ck_agent_run_status CHECK (status IN (
        'RECEIVED', 'PLANNED', 'WAITING_CLARIFICATION', 'WAITING_CONFIRMATION',
        'EXECUTING', 'SUCCEEDED', 'FAILED', 'REJECTED', 'CANCELLED', 'TIMED_OUT',
        'RECONCILIATION_REQUIRED'
    ))
);

CREATE INDEX idx_agent_runs_scope_time
    ON agent_runs(school_id, canteen_id, created_at, run_id);

CREATE INDEX idx_agent_runs_actor_status
    ON agent_runs(actor_user_id, status, updated_at);

CREATE TABLE agent_steps (
    run_id VARCHAR(64) NOT NULL,
    step_id VARCHAR(128) NOT NULL,
    step_order INTEGER NOT NULL,
    tool_name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    input_digest CHAR(64) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    result_json TEXT,
    error_code VARCHAR(64),
    error_message VARCHAR(2000),
    started_at TIMESTAMP NULL,
    finished_at TIMESTAMP NULL,
    PRIMARY KEY (run_id, step_id),
    CONSTRAINT uk_agent_step_idempotency UNIQUE (run_id, idempotency_key),
    CONSTRAINT fk_agent_step_run FOREIGN KEY (run_id) REFERENCES agent_runs(run_id),
    CONSTRAINT ck_agent_step_status CHECK (status IN (
        'PENDING', 'CLAIMED', 'EXECUTING', 'SUCCEEDED', 'FAILED', 'TIMED_OUT',
        'RECONCILIATION_REQUIRED', 'CANCELLED'
    ))
);

CREATE TABLE agent_run_decisions (
    decision_id VARCHAR(64) PRIMARY KEY,
    run_id VARCHAR(64) NOT NULL,
    decision_type VARCHAR(32) NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    actor_user_id VARCHAR(64) NOT NULL,
    plan_hash CHAR(64) NOT NULL,
    expires_at TIMESTAMP NULL,
    comment VARCHAR(2000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_agent_decision_run FOREIGN KEY (run_id) REFERENCES agent_runs(run_id),
    CONSTRAINT ck_agent_decision_type CHECK (
        decision_type IN ('RUN_CONFIRM', 'RUN_REJECT', 'RUN_CANCEL')
    ),
    CONSTRAINT ck_agent_decision_outcome CHECK (
        outcome IN ('ACCEPTED', 'REJECTED', 'CANCELLED')
    )
);

CREATE INDEX idx_agent_decisions_run_time
    ON agent_run_decisions(run_id, created_at, decision_id);

CREATE TABLE agent_run_events (
    event_id VARCHAR(64) PRIMARY KEY,
    run_id VARCHAR(64) NOT NULL,
    event_sequence BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    from_status VARCHAR(32),
    to_status VARCHAR(32) NOT NULL,
    actor_user_id VARCHAR(64),
    payload_json TEXT,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_agent_event_sequence UNIQUE (run_id, event_sequence),
    CONSTRAINT fk_agent_event_run FOREIGN KEY (run_id) REFERENCES agent_runs(run_id)
);

CREATE INDEX idx_agent_events_run_time
    ON agent_run_events(run_id, occurred_at, event_sequence);
