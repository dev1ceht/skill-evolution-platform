-- Phase 5/6: make run decisions replay-safe independently from the Run version.

ALTER TABLE agent_run_decisions
    ADD idempotency_key VARCHAR(128);

-- V11 had no decision idempotency column. Backfill existing records; new writes
-- require a non-blank key in the AgentRunDecision value object.
UPDATE agent_run_decisions
SET idempotency_key = decision_id
WHERE idempotency_key IS NULL;

CREATE UNIQUE INDEX uk_agent_decision_idempotency
    ON agent_run_decisions(run_id, actor_user_id, idempotency_key);
