CREATE TABLE assistant_conversations (
    conversation_id VARCHAR(64) PRIMARY KEY,
    actor_user_id VARCHAR(64) NOT NULL,
    actor_username VARCHAR(128) NOT NULL,
    school_id VARCHAR(64) NOT NULL,
    canteen_id VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_assistant_conversation_status CHECK (status IN ('ACTIVE', 'CLOSED'))
);

CREATE INDEX idx_assistant_conversations_owner
    ON assistant_conversations(actor_user_id, school_id, canteen_id, updated_at);

CREATE TABLE assistant_turns (
    turn_id VARCHAR(64) PRIMARY KEY,
    conversation_id VARCHAR(64) NOT NULL,
    turn_sequence BIGINT NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    message TEXT NOT NULL,
    response_json TEXT NOT NULL,
    kind VARCHAR(32) NOT NULL,
    intent VARCHAR(128),
    run_id VARCHAR(64),
    run_status VARCHAR(32),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_assistant_turn_sequence UNIQUE (conversation_id, turn_sequence),
    CONSTRAINT uk_assistant_turn_idempotency UNIQUE (conversation_id, idempotency_key),
    CONSTRAINT fk_assistant_turn_conversation
        FOREIGN KEY (conversation_id) REFERENCES assistant_conversations(conversation_id),
    CONSTRAINT ck_assistant_turn_kind CHECK (kind IN ('CLARIFICATION', 'UNSUPPORTED', 'RESULT'))
);

CREATE INDEX idx_assistant_turns_conversation_time
    ON assistant_turns(conversation_id, created_at, turn_sequence);
