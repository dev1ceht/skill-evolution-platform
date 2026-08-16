-- Phase 5: bind decision idempotency to the complete normalized request.
-- Existing decisions remain readable; a null hash is treated as legacy and is
-- never silently replayed as a new request.

ALTER TABLE agent_run_decisions
    ADD request_hash CHAR(64) NULL;
