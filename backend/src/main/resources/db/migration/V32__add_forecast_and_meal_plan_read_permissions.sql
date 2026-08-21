-- SC-007: expose read-only forecast and meal-prep analysis to operations personas.

INSERT INTO permissions (permission_code, name, resource, action, description)
VALUES
    ('TRAFFIC_FORECAST_READ', '查看客流预测', 'traffic_forecast', 'read', '读取当前范围内版本化客流预测事实'),
    ('MEAL_PLAN_ANALYSIS_READ', '查看备餐建议', 'meal_plan', 'read', '按客流预测和已发布菜单生成只读备餐建议');

INSERT INTO role_permissions (role_code, permission_code)
VALUES
    ('SYSTEM_ADMIN', 'TRAFFIC_FORECAST_READ'),
    ('SCHOOL_ADMIN', 'TRAFFIC_FORECAST_READ'),
    ('CANTEEN_STAFF', 'TRAFFIC_FORECAST_READ'),
    ('SYSTEM_ADMIN', 'MEAL_PLAN_ANALYSIS_READ'),
    ('SCHOOL_ADMIN', 'MEAL_PLAN_ANALYSIS_READ'),
    ('CANTEEN_STAFF', 'MEAL_PLAN_ANALYSIS_READ');
