CREATE TABLE agent_run_claims (
    run_id VARCHAR(64) PRIMARY KEY,
    owner_id VARCHAR(128) NOT NULL,
    claim_token VARCHAR(64) NOT NULL,
    claimed_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_agent_run_claim_token UNIQUE (claim_token),
    CONSTRAINT ck_agent_run_claim_window CHECK (expires_at > claimed_at),
    CONSTRAINT fk_agent_run_claim FOREIGN KEY (run_id) REFERENCES agent_runs(run_id)
);

CREATE INDEX idx_agent_run_claims_expiry
    ON agent_run_claims(expires_at, run_id);
