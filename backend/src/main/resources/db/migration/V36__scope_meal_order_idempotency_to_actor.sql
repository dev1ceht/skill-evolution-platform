ALTER TABLE meal_orders
    DROP CONSTRAINT uk_meal_orders_scope_idempotency;

ALTER TABLE meal_orders
    ADD CONSTRAINT uk_meal_orders_actor_idempotency
        UNIQUE (school_id, canteen_id, actor_user_id, idempotency_key);

ALTER TABLE meal_orders
    DROP CONSTRAINT ck_meal_order_payment;

ALTER TABLE meal_orders
    ADD CONSTRAINT ck_meal_order_payment CHECK (payment_status = 'UNPAID');
