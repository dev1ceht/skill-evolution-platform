-- Bind a procurement-plan conversion to the complete normalized order request.
-- Nullable keeps existing converted plans readable; the application backfills the hash on the
-- first safe idempotent replay while all new links persist it atomically.
ALTER TABLE procurement_plan_orders
    ADD payload_hash VARCHAR(64);

