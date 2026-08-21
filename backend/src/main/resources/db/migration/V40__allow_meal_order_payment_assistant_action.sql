ALTER TABLE assistant_pending_actions
    DROP CONSTRAINT ck_assistant_pending_action_intent;

ALTER TABLE assistant_pending_actions
    ADD CONSTRAINT ck_assistant_pending_action_intent CHECK (
        intent IN (
            'menu.publish',
            'procurement.plan.generate',
            'procurement.order.create',
            'procurement.order.receive',
            'meal_order.create',
            'meal_order.cancel',
            'meal_order.pay',
            'meal_review.create',
            'diner_complaint.create',
            'inventory.receive',
            'inventory.stock-out',
            'alert.dispose'
        )
    );
