CREATE TABLE assistant_clarifications (
    conversation_id VARCHAR(64) PRIMARY KEY,
    intent VARCHAR(128) NOT NULL,
    original_message VARCHAR(2000) NOT NULL,
    missing_fields VARCHAR(512) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_assistant_clarification_conversation
        FOREIGN KEY (conversation_id) REFERENCES assistant_conversations(conversation_id)
);

CREATE INDEX idx_assistant_clarifications_updated
    ON assistant_clarifications(updated_at);

ALTER TABLE assistant_conversations
    DROP CONSTRAINT ck_assistant_conversation_status;

ALTER TABLE assistant_conversations
    ADD CONSTRAINT ck_assistant_conversation_status CHECK (
        status IN ('ACTIVE', 'WAITING_CLARIFICATION', 'CLOSED')
    );
