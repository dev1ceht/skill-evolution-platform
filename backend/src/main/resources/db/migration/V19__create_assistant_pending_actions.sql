CREATE TABLE assistant_pending_actions (
    conversation_id VARCHAR(64) PRIMARY KEY,
    intent VARCHAR(128) NOT NULL,
    run_id VARCHAR(64) NOT NULL,
    run_version BIGINT NOT NULL,
    menu_id VARCHAR(64) NOT NULL,
    menu_version BIGINT NOT NULL,
    plan_hash CHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_assistant_pending_action_conversation
        FOREIGN KEY (conversation_id) REFERENCES assistant_conversations(conversation_id),
    CONSTRAINT uk_assistant_pending_action_run UNIQUE (run_id),
    CONSTRAINT ck_assistant_pending_action_intent CHECK (intent IN ('menu.publish')),
    CONSTRAINT ck_assistant_pending_action_versions CHECK (run_version >= 0 AND menu_version >= 0)
);

CREATE INDEX idx_assistant_pending_actions_updated
    ON assistant_pending_actions(updated_at);

ALTER TABLE assistant_conversations
    DROP CONSTRAINT ck_assistant_conversation_status;

ALTER TABLE assistant_conversations
    ADD CONSTRAINT ck_assistant_conversation_status CHECK (
        status IN ('ACTIVE', 'WAITING_CLARIFICATION', 'WAITING_CONFIRMATION', 'CLOSED')
    );

ALTER TABLE assistant_turns
    DROP CONSTRAINT ck_assistant_turn_kind;

ALTER TABLE assistant_turns
    ADD CONSTRAINT ck_assistant_turn_kind CHECK (
        kind IN ('CLARIFICATION', 'UNSUPPORTED', 'RESULT', 'CONFIRMATION_REQUIRED')
    );
