-- Phase 7: expose the business duties used by confirmation-gated write Skills.

INSERT INTO permissions (permission_code, name, resource, action, description)
VALUES
    ('PROCUREMENT_PLAN_WRITE', '生成采购计划', 'procurement_plan', 'write', '根据已发布菜单生成采购计划'),
    ('PROCUREMENT_ORDER_WRITE', '创建采购订单', 'purchase_order', 'write', '创建采购订单'),
    ('PROCUREMENT_RECEIVE', '采购收货', 'purchase_order', 'receive', '验收采购订单并写入批次库存'),
    ('INVENTORY_RECEIVE', '库存入库', 'inventory', 'receive', '登记库存入库'),
    ('INVENTORY_STOCK_OUT', '库存出库', 'inventory', 'stock_out', '登记库存出库'),
    ('ALERT_DISPOSE', '处置预警', 'alert', 'dispose', '处置当前范围内的预警');

INSERT INTO role_permissions (role_code, permission_code)
VALUES
    ('SYSTEM_ADMIN', 'PROCUREMENT_PLAN_WRITE'),
    ('SYSTEM_ADMIN', 'PROCUREMENT_ORDER_WRITE'),
    ('SYSTEM_ADMIN', 'PROCUREMENT_RECEIVE'),
    ('SYSTEM_ADMIN', 'INVENTORY_RECEIVE'),
    ('SYSTEM_ADMIN', 'INVENTORY_STOCK_OUT'),
    ('SYSTEM_ADMIN', 'ALERT_DISPOSE'),
    ('SCHOOL_ADMIN', 'PROCUREMENT_PLAN_WRITE'),
    ('SCHOOL_ADMIN', 'PROCUREMENT_ORDER_WRITE'),
    ('SCHOOL_ADMIN', 'PROCUREMENT_RECEIVE'),
    ('SCHOOL_ADMIN', 'INVENTORY_RECEIVE'),
    ('SCHOOL_ADMIN', 'INVENTORY_STOCK_OUT'),
    ('SCHOOL_ADMIN', 'ALERT_DISPOSE'),
    ('CANTEEN_STAFF', 'PROCUREMENT_PLAN_WRITE'),
    ('CANTEEN_STAFF', 'PROCUREMENT_ORDER_WRITE'),
    ('CANTEEN_STAFF', 'PROCUREMENT_RECEIVE'),
    ('CANTEEN_STAFF', 'INVENTORY_RECEIVE'),
    ('CANTEEN_STAFF', 'INVENTORY_STOCK_OUT'),
    ('CANTEEN_STAFF', 'ALERT_DISPOSE'),
    ('REGULATOR', 'ALERT_DISPOSE');

ALTER TABLE assistant_pending_actions
    DROP CONSTRAINT ck_assistant_pending_action_intent;

ALTER TABLE assistant_pending_actions
    ADD CONSTRAINT ck_assistant_pending_action_intent CHECK (
        intent IN (
            'menu.publish',
            'procurement.plan.generate',
            'procurement.order.create',
            'procurement.order.receive',
            'inventory.receive',
            'inventory.stock-out',
            'alert.dispose'
        )
    );
