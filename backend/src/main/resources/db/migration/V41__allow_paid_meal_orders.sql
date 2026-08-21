ALTER TABLE meal_orders
    DROP CONSTRAINT ck_meal_order_payment;

ALTER TABLE meal_orders
    ADD CONSTRAINT ck_meal_order_payment CHECK (payment_status IN ('UNPAID', 'PAID'));
