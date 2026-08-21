-- Smart Canteen study dataset.
-- This is a reproducible local learning fixture generated from the dataset manifest.
-- It writes ordinary domain records so the existing services and Agent tools can query them.

SET NAMES utf8mb4;
START TRANSACTION;

INSERT INTO schools (id, name)
VALUES ('SCHOOL-001', '智慧食堂研究学校')
ON DUPLICATE KEY UPDATE name = VALUES(name);

INSERT INTO canteens (id, school_id, name, address, status)
VALUES ('CANTEEN-001', 'SCHOOL-001', '一食堂', '研究园区东区', 'ACTIVE')
ON DUPLICATE KEY UPDATE name = VALUES(name), address = VALUES(address), status = VALUES(status);

INSERT INTO ingredients (
    school_id, canteen_id, ingredient_id, name, category, base_unit, specification,
    energy_kcal, protein_g, fat_g, carbohydrate_g, warning_threshold, status
)
VALUES
    ('SCHOOL-001', 'CANTEEN-001', 'ING-CHICKEN', '鸡胸肉', '肉禽', 'kg', '去皮冷鲜 10kg/箱', 165, 31, 3.6, 0, 20, 'ACTIVE'),
    ('SCHOOL-001', 'CANTEEN-001', 'ING-PORK', '五花肉', '肉禽', 'kg', '冷鲜 10kg/箱', 395, 14, 35, 0, 10, 'ACTIVE'),
    ('SCHOOL-001', 'CANTEEN-001', 'ING-BEEF', '牛腩', '肉禽', 'kg', '冷鲜 10kg/箱', 250, 18, 20, 0, 15, 'ACTIVE'),
    ('SCHOOL-001', 'CANTEEN-001', 'ING-BROCCOLI', '西兰花', '蔬菜', 'kg', '净菜 5kg/箱', 34, 2.8, 0.4, 6.6, 12, 'ACTIVE'),
    ('SCHOOL-001', 'CANTEEN-001', 'ING-POTATO', '土豆', '蔬菜', 'kg', '一级品 10kg/袋', 77, 2, 0.1, 17, 20, 'ACTIVE'),
    ('SCHOOL-001', 'CANTEEN-001', 'ING-TOMATO', '西红柿', '蔬菜', 'kg', '一级品 5kg/箱', 18, 0.9, 0.2, 3.9, 12, 'ACTIVE'),
    ('SCHOOL-001', 'CANTEEN-001', 'ING-EGG', '鸡蛋', '蛋奶', 'count', '鲜鸡蛋 30枚/盒', 143, 12.6, 9.5, 1.1, 60, 'ACTIVE'),
    ('SCHOOL-001', 'CANTEEN-001', 'ING-OIL', '食用油', '调味品', 'L', '非转基因大豆油 5L/桶', 884, 0, 100, 0, 5, 'ACTIVE'),
    ('SCHOOL-001', 'CANTEEN-001', 'ING-SOY', '生抽', '调味品', 'L', '酿造酱油 1.9L/瓶', 53, 5.6, 0, 8, 0.5, 'ACTIVE')
ON DUPLICATE KEY UPDATE
    name = VALUES(name), category = VALUES(category), base_unit = VALUES(base_unit),
    specification = VALUES(specification), energy_kcal = VALUES(energy_kcal),
    protein_g = VALUES(protein_g), fat_g = VALUES(fat_g),
    carbohydrate_g = VALUES(carbohydrate_g), warning_threshold = VALUES(warning_threshold),
    status = VALUES(status), updated_at = CURRENT_TIMESTAMP;

INSERT INTO ingredient_units (
    school_id, canteen_id, ingredient_id, unit_code, base_unit, to_base_factor, status
)
VALUES
    ('SCHOOL-001', 'CANTEEN-001', 'ING-CHICKEN', 'kg', 'kg', 1, 'ACTIVE'),
    ('SCHOOL-001', 'CANTEEN-001', 'ING-PORK', 'kg', 'kg', 1, 'ACTIVE'),
    ('SCHOOL-001', 'CANTEEN-001', 'ING-BEEF', 'kg', 'kg', 1, 'ACTIVE'),
    ('SCHOOL-001', 'CANTEEN-001', 'ING-BROCCOLI', 'kg', 'kg', 1, 'ACTIVE'),
    ('SCHOOL-001', 'CANTEEN-001', 'ING-POTATO', 'kg', 'kg', 1, 'ACTIVE'),
    ('SCHOOL-001', 'CANTEEN-001', 'ING-TOMATO', 'kg', 'kg', 1, 'ACTIVE'),
    ('SCHOOL-001', 'CANTEEN-001', 'ING-EGG', 'count', 'count', 1, 'ACTIVE'),
    ('SCHOOL-001', 'CANTEEN-001', 'ING-OIL', 'L', 'L', 1, 'ACTIVE'),
    ('SCHOOL-001', 'CANTEEN-001', 'ING-SOY', 'L', 'L', 1, 'ACTIVE')
ON DUPLICATE KEY UPDATE
    base_unit = VALUES(base_unit), to_base_factor = VALUES(to_base_factor), status = VALUES(status);

INSERT INTO dishes (
    school_id, canteen_id, dish_id, name, category, description, status, version
)
VALUES
    ('SCHOOL-001', 'CANTEEN-001', 'DISH-001', '番茄炒蛋', '家常菜', '酸甜适口，适合大众就餐。', 'ACTIVE', 1),
    ('SCHOOL-001', 'CANTEEN-001', 'DISH-002', '宫保鸡丁', '荤菜', '鸡肉配花生和土豆丁，微辣。', 'ACTIVE', 1),
    ('SCHOOL-001', 'CANTEEN-001', 'DISH-003', '红烧肉', '荤菜', '小火焖制，肥瘦相间。', 'ACTIVE', 1),
    ('SCHOOL-001', 'CANTEEN-001', 'DISH-004', '西兰花炒牛肉', '荤素搭配', '牛肉与西兰花快炒。', 'ACTIVE', 1),
    ('SCHOOL-001', 'CANTEEN-001', 'DISH-005', '土豆牛腩', '荤菜', '牛腩与土豆慢炖。', 'ACTIVE', 1)
ON DUPLICATE KEY UPDATE
    name = VALUES(name), category = VALUES(category), description = VALUES(description),
    status = VALUES(status), version = VALUES(version), updated_at = CURRENT_TIMESTAMP;

DELETE FROM dish_ingredients
WHERE school_id = 'SCHOOL-001' AND canteen_id = 'CANTEEN-001'
  AND dish_id IN ('DISH-001', 'DISH-002', 'DISH-003', 'DISH-004', 'DISH-005');

INSERT INTO dish_ingredients (
    school_id, canteen_id, dish_id, ingredient_id, quantity, unit
)
VALUES
    ('SCHOOL-001', 'CANTEEN-001', 'DISH-001', 'ING-EGG', 1, 'count'),
    ('SCHOOL-001', 'CANTEEN-001', 'DISH-001', 'ING-TOMATO', 0.08, 'kg'),
    ('SCHOOL-001', 'CANTEEN-001', 'DISH-001', 'ING-OIL', 0.01, 'L'),
    ('SCHOOL-001', 'CANTEEN-001', 'DISH-002', 'ING-CHICKEN', 0.12, 'kg'),
    ('SCHOOL-001', 'CANTEEN-001', 'DISH-002', 'ING-POTATO', 0.04, 'kg'),
    ('SCHOOL-001', 'CANTEEN-001', 'DISH-002', 'ING-OIL', 0.01, 'L'),
    ('SCHOOL-001', 'CANTEEN-001', 'DISH-003', 'ING-PORK', 0.13, 'kg'),
    ('SCHOOL-001', 'CANTEEN-001', 'DISH-003', 'ING-POTATO', 0.05, 'kg'),
    ('SCHOOL-001', 'CANTEEN-001', 'DISH-003', 'ING-SOY', 0.01, 'L'),
    ('SCHOOL-001', 'CANTEEN-001', 'DISH-003', 'ING-OIL', 0.005, 'L'),
    ('SCHOOL-001', 'CANTEEN-001', 'DISH-004', 'ING-BEEF', 0.08, 'kg'),
    ('SCHOOL-001', 'CANTEEN-001', 'DISH-004', 'ING-BROCCOLI', 0.1, 'kg'),
    ('SCHOOL-001', 'CANTEEN-001', 'DISH-004', 'ING-OIL', 0.01, 'L'),
    ('SCHOOL-001', 'CANTEEN-001', 'DISH-005', 'ING-BEEF', 0.1, 'kg'),
    ('SCHOOL-001', 'CANTEEN-001', 'DISH-005', 'ING-POTATO', 0.15, 'kg'),
    ('SCHOOL-001', 'CANTEEN-001', 'DISH-005', 'ING-TOMATO', 0.04, 'kg'),
    ('SCHOOL-001', 'CANTEEN-001', 'DISH-005', 'ING-OIL', 0.008, 'L');

INSERT INTO daily_menus (
    school_id, canteen_id, menu_id, menu_date, meal_time, status, version
)
VALUES
    ('SCHOOL-001', 'CANTEEN-001', 'M818', '2026-08-18', 'LUNCH', 'PUBLISHED', 1),
    ('SCHOOL-001', 'CANTEEN-001', 'M819', '2026-08-19', 'LUNCH', 'PUBLISHED', 1),
    ('SCHOOL-001', 'CANTEEN-001', 'M820', '2026-08-20', 'LUNCH', 'PUBLISHED', 1),
    ('SCHOOL-001', 'CANTEEN-001', 'M821', '2026-08-21', 'LUNCH', 'PUBLISHED', 1),
    ('SCHOOL-001', 'CANTEEN-001', 'M822', '2026-08-22', 'LUNCH', 'PUBLISHED', 1)
ON DUPLICATE KEY UPDATE
    menu_date = VALUES(menu_date), meal_time = VALUES(meal_time),
    status = VALUES(status), version = VALUES(version), updated_at = CURRENT_TIMESTAMP;

INSERT INTO traffic_forecasts (
    school_id, canteen_id, forecast_date, meal_time, expected_diner_count,
    lower_bound, upper_bound, model_version, source, generated_at
)
VALUES (
    'SCHOOL-001', 'CANTEEN-001', '2026-08-22', 'LUNCH', 850,
    810, 880, 'study-traffic-v1', 'GENERATED_STUDY_FACT', '2026-08-21 09:00:00'
)
ON DUPLICATE KEY UPDATE
    expected_diner_count = VALUES(expected_diner_count),
    lower_bound = VALUES(lower_bound), upper_bound = VALUES(upper_bound),
    model_version = VALUES(model_version), source = VALUES(source),
    generated_at = VALUES(generated_at);

DELETE FROM daily_menu_items
WHERE school_id = 'SCHOOL-001' AND canteen_id = 'CANTEEN-001'
  AND menu_id IN ('M818', 'M819', 'M820', 'M821', 'M822');

INSERT INTO daily_menu_items (
    school_id, canteen_id, menu_id, dish_id, estimated_quantity, sort_order
)
VALUES
    ('SCHOOL-001', 'CANTEEN-001', 'M818', 'DISH-001', 200, 1),
    ('SCHOOL-001', 'CANTEEN-001', 'M818', 'DISH-002', 220, 2),
    ('SCHOOL-001', 'CANTEEN-001', 'M818', 'DISH-003', 150, 3),
    ('SCHOOL-001', 'CANTEEN-001', 'M818', 'DISH-004', 160, 4),
    ('SCHOOL-001', 'CANTEEN-001', 'M819', 'DISH-001', 210, 1),
    ('SCHOOL-001', 'CANTEEN-001', 'M819', 'DISH-002', 230, 2),
    ('SCHOOL-001', 'CANTEEN-001', 'M819', 'DISH-003', 155, 3),
    ('SCHOOL-001', 'CANTEEN-001', 'M819', 'DISH-005', 120, 4),
    ('SCHOOL-001', 'CANTEEN-001', 'M820', 'DISH-001', 205, 1),
    ('SCHOOL-001', 'CANTEEN-001', 'M820', 'DISH-002', 225, 2),
    ('SCHOOL-001', 'CANTEEN-001', 'M820', 'DISH-004', 170, 3),
    ('SCHOOL-001', 'CANTEEN-001', 'M820', 'DISH-005', 125, 4),
    ('SCHOOL-001', 'CANTEEN-001', 'M821', 'DISH-001', 215, 1),
    ('SCHOOL-001', 'CANTEEN-001', 'M821', 'DISH-002', 235, 2),
    ('SCHOOL-001', 'CANTEEN-001', 'M821', 'DISH-003', 145, 3),
    ('SCHOOL-001', 'CANTEEN-001', 'M821', 'DISH-004', 175, 4),
    ('SCHOOL-001', 'CANTEEN-001', 'M822', 'DISH-001', 220, 1),
    ('SCHOOL-001', 'CANTEEN-001', 'M822', 'DISH-002', 240, 2),
    ('SCHOOL-001', 'CANTEEN-001', 'M822', 'DISH-003', 160, 3),
    ('SCHOOL-001', 'CANTEEN-001', 'M822', 'DISH-004', 180, 4),
    ('SCHOOL-001', 'CANTEEN-001', 'M822', 'DISH-005', 130, 5);

INSERT INTO suppliers (
    school_id, canteen_id, supplier_id, name, contact_name, contact_phone, license_no, status
)
VALUES
    ('SCHOOL-001', 'CANTEEN-001', 'SUP-001', '鲜优冷链供应链', '王强', '13800000001', 'LIC-SUP-001', 'ACTIVE'),
    ('SCHOOL-001', 'CANTEEN-001', 'SUP-002', '绿源蔬菜配送', '李芳', '13800000002', 'LIC-SUP-002', 'ACTIVE'),
    ('SCHOOL-001', 'CANTEEN-001', 'SUP-003', '粮油调味品中心', '赵磊', '13800000003', 'LIC-SUP-003', 'ACTIVE')
ON DUPLICATE KEY UPDATE
    name = VALUES(name), contact_name = VALUES(contact_name), contact_phone = VALUES(contact_phone),
    license_no = VALUES(license_no), status = VALUES(status), updated_at = CURRENT_TIMESTAMP;

INSERT INTO purchase_orders (
    school_id, canteen_id, order_id, order_no, supplier_id, order_type, status,
    expected_delivery_at, total_amount, remark, idempotency_key
)
VALUES
    ('SCHOOL-001', 'CANTEEN-001', 'PO-20260820-001', 'PO20260820001', 'SUP-001', 'OFFLINE', 'RECEIVED', '2026-08-20 09:00:00', 1884.00, '已收货基础库存', 'seed-po-20260820-001'),
    ('SCHOOL-001', 'CANTEEN-001', 'PO-20260821-001', 'PO20260821001', 'SUP-001', 'OFFLINE', 'CONFIRMED', '2026-08-21 18:00:00', 582.00, '明日备餐在途采购', 'seed-po-20260821-001')
ON DUPLICATE KEY UPDATE
    supplier_id = VALUES(supplier_id), status = VALUES(status), expected_delivery_at = VALUES(expected_delivery_at),
    total_amount = VALUES(total_amount), remark = VALUES(remark), updated_at = CURRENT_TIMESTAMP;

DELETE FROM purchase_order_items
WHERE school_id = 'SCHOOL-001' AND canteen_id = 'CANTEEN-001'
  AND order_id IN ('PO-20260820-001', 'PO-20260821-001');

INSERT INTO purchase_order_items (
    school_id, canteen_id, order_id, ingredient_id, quantity, unit, unit_price, amount, received_quantity_base
)
VALUES
    ('SCHOOL-001', 'CANTEEN-001', 'PO-20260820-001', 'ING-CHICKEN', 12, 'kg', 20, 240, 12),
    ('SCHOOL-001', 'CANTEEN-001', 'PO-20260820-001', 'ING-PORK', 26, 'kg', 22, 572, 26),
    ('SCHOOL-001', 'CANTEEN-001', 'PO-20260820-001', 'ING-BEEF', 11, 'kg', 36, 396, 11),
    ('SCHOOL-001', 'CANTEEN-001', 'PO-20260820-001', 'ING-BROCCOLI', 10, 'kg', 8, 80, 10),
    ('SCHOOL-001', 'CANTEEN-001', 'PO-20260820-001', 'ING-POTATO', 45, 'kg', 4, 180, 45),
    ('SCHOOL-001', 'CANTEEN-001', 'PO-20260820-001', 'ING-TOMATO', 12, 'kg', 6, 72, 12),
    ('SCHOOL-001', 'CANTEEN-001', 'PO-20260820-001', 'ING-EGG', 180, 'count', 0.8, 144, 180),
    ('SCHOOL-001', 'CANTEEN-001', 'PO-20260820-001', 'ING-OIL', 15, 'L', 12, 180, 15),
    ('SCHOOL-001', 'CANTEEN-001', 'PO-20260820-001', 'ING-SOY', 2, 'L', 10, 20, 2),
    ('SCHOOL-001', 'CANTEEN-001', 'PO-20260821-001', 'ING-CHICKEN', 10, 'kg', 20, 200, 0),
    ('SCHOOL-001', 'CANTEEN-001', 'PO-20260821-001', 'ING-BEEF', 8, 'kg', 35, 280, 0),
    ('SCHOOL-001', 'CANTEEN-001', 'PO-20260821-001', 'ING-BROCCOLI', 6, 'kg', 8, 48, 0),
    ('SCHOOL-001', 'CANTEEN-001', 'PO-20260821-001', 'ING-TOMATO', 5, 'kg', 6, 30, 0),
    ('SCHOOL-001', 'CANTEEN-001', 'PO-20260821-001', 'ING-EGG', 30, 'count', 0.8, 24, 0);

INSERT INTO purchase_receipts (
    school_id, canteen_id, receipt_id, order_id, idempotency_key, received_at
)
VALUES ('SCHOOL-001', 'CANTEEN-001', 'RECEIPT-20260820-001', 'PO-20260820-001', 'seed-receipt-20260820-001', '2026-08-20 09:30:00')
ON DUPLICATE KEY UPDATE received_at = VALUES(received_at);

INSERT INTO inventory (
    school_id, canteen_id, material_id, quantity_base, base_unit, warning_threshold, last_update_time
)
VALUES
    ('SCHOOL-001', 'CANTEEN-001', 'ING-CHICKEN', 12, 'kg', 20, '2026-08-20 09:30:00'),
    ('SCHOOL-001', 'CANTEEN-001', 'ING-PORK', 26, 'kg', 10, '2026-08-20 09:30:00'),
    ('SCHOOL-001', 'CANTEEN-001', 'ING-BEEF', 11, 'kg', 15, '2026-08-20 09:30:00'),
    ('SCHOOL-001', 'CANTEEN-001', 'ING-BROCCOLI', 10, 'kg', 12, '2026-08-20 09:30:00'),
    ('SCHOOL-001', 'CANTEEN-001', 'ING-POTATO', 45, 'kg', 20, '2026-08-20 09:30:00'),
    ('SCHOOL-001', 'CANTEEN-001', 'ING-TOMATO', 12, 'kg', 12, '2026-08-20 09:30:00'),
    ('SCHOOL-001', 'CANTEEN-001', 'ING-EGG', 180, 'count', 60, '2026-08-20 09:30:00'),
    ('SCHOOL-001', 'CANTEEN-001', 'ING-OIL', 15, 'L', 5, '2026-08-20 09:30:00'),
    ('SCHOOL-001', 'CANTEEN-001', 'ING-SOY', 2, 'L', 0.5, '2026-08-20 09:30:00')
ON DUPLICATE KEY UPDATE
    quantity_base = VALUES(quantity_base), base_unit = VALUES(base_unit),
    warning_threshold = VALUES(warning_threshold), last_update_time = VALUES(last_update_time);

INSERT INTO inventory_batches (
    school_id, canteen_id, batch_id, order_id, ingredient_id, supplier_id, batch_no,
    quantity_base, base_unit, purchase_price, production_date, expiry_date, trace_code
)
VALUES
    ('SCHOOL-001', 'CANTEEN-001', 'BATCH-CHICKEN-20260820', 'PO-20260820-001', 'ING-CHICKEN', 'SUP-001', 'CH-0820-A', 12, 'kg', 20, '2026-08-19', '2026-08-24', 'TRACE-CHICKEN-20260820'),
    ('SCHOOL-001', 'CANTEEN-001', 'BATCH-PORK-20260820', 'PO-20260820-001', 'ING-PORK', 'SUP-001', 'PK-0820-A', 26, 'kg', 22, '2026-08-19', '2026-08-25', 'TRACE-PORK-20260820'),
    ('SCHOOL-001', 'CANTEEN-001', 'BATCH-BEEF-20260820', 'PO-20260820-001', 'ING-BEEF', 'SUP-001', 'BF-0820-A', 11, 'kg', 36, '2026-08-19', '2026-08-25', 'TRACE-BEEF-20260820'),
    ('SCHOOL-001', 'CANTEEN-001', 'BATCH-BROCCOLI-20260820', 'PO-20260820-001', 'ING-BROCCOLI', 'SUP-002', 'BR-0820-A', 10, 'kg', 8, '2026-08-20', '2026-08-23', 'TRACE-BROCCOLI-20260820'),
    ('SCHOOL-001', 'CANTEEN-001', 'BATCH-POTATO-20260820', 'PO-20260820-001', 'ING-POTATO', 'SUP-002', 'PT-0820-A', 45, 'kg', 4, '2026-08-19', '2026-08-29', 'TRACE-POTATO-20260820'),
    ('SCHOOL-001', 'CANTEEN-001', 'BATCH-TOMATO-20260820', 'PO-20260820-001', 'ING-TOMATO', 'SUP-002', 'TM-0820-A', 12, 'kg', 6, '2026-08-20', '2026-08-24', 'TRACE-TOMATO-20260820'),
    ('SCHOOL-001', 'CANTEEN-001', 'BATCH-EGG-20260820', 'PO-20260820-001', 'ING-EGG', 'SUP-001', 'EG-0820-A', 180, 'count', 0.8, '2026-08-19', '2026-09-02', 'TRACE-EGG-20260820'),
    ('SCHOOL-001', 'CANTEEN-001', 'BATCH-OIL-20260820', 'PO-20260820-001', 'ING-OIL', 'SUP-003', 'OL-0820-A', 15, 'L', 12, '2026-08-01', '2027-02-01', 'TRACE-OIL-20260820'),
    ('SCHOOL-001', 'CANTEEN-001', 'BATCH-SOY-20260820', 'PO-20260820-001', 'ING-SOY', 'SUP-003', 'SY-0820-A', 2, 'L', 10, '2026-07-01', '2027-01-01', 'TRACE-SOY-20260820')
ON DUPLICATE KEY UPDATE
    quantity_base = VALUES(quantity_base), expiry_date = VALUES(expiry_date),
    purchase_price = VALUES(purchase_price), trace_code = VALUES(trace_code);

DELETE FROM purchase_receipt_items
WHERE school_id = 'SCHOOL-001' AND canteen_id = 'CANTEEN-001' AND receipt_id = 'RECEIPT-20260820-001';

INSERT INTO purchase_receipt_items (
    school_id, canteen_id, receipt_id, batch_id, ingredient_id, quantity_base, base_unit
)
SELECT 'SCHOOL-001', 'CANTEEN-001', 'RECEIPT-20260820-001', batch_id, ingredient_id, quantity_base, base_unit
FROM inventory_batches
WHERE school_id = 'SCHOOL-001' AND canteen_id = 'CANTEEN-001'
  AND order_id = 'PO-20260820-001';

DELETE FROM traceability_records
WHERE school_id = 'SCHOOL-001' AND canteen_id = 'CANTEEN-001'
  AND trace_code LIKE 'TRACE-%-20260820';

INSERT INTO traceability_records (
    school_id, canteen_id, trace_code, batch_id, order_id, ingredient_id, supplier_id, quantity_base, base_unit
)
SELECT school_id, canteen_id, trace_code, batch_id, order_id, ingredient_id, supplier_id, quantity_base, base_unit
FROM inventory_batches
WHERE school_id = 'SCHOOL-001' AND canteen_id = 'CANTEEN-001'
  AND order_id = 'PO-20260820-001';

INSERT INTO procurement_plans (
    school_id, canteen_id, plan_id, plan_no, period_start, period_end, status, idempotency_key, version
)
VALUES ('SCHOOL-001', 'CANTEEN-001', 'PLAN-20260822-001', 'PLAN20260822001', '2026-08-22', '2026-08-22', 'DRAFT', 'seed-plan-20260822-001', 1)
ON DUPLICATE KEY UPDATE status = VALUES(status), version = VALUES(version), updated_at = CURRENT_TIMESTAMP;

DELETE FROM procurement_plan_menus
WHERE school_id = 'SCHOOL-001' AND canteen_id = 'CANTEEN-001' AND plan_id = 'PLAN-20260822-001';
INSERT INTO procurement_plan_menus (school_id, canteen_id, plan_id, menu_id)
VALUES ('SCHOOL-001', 'CANTEEN-001', 'PLAN-20260822-001', 'M822');

DELETE FROM procurement_plan_items
WHERE school_id = 'SCHOOL-001' AND canteen_id = 'CANTEEN-001' AND plan_id = 'PLAN-20260822-001';
INSERT INTO procurement_plan_items (
    school_id, canteen_id, plan_id, ingredient_id, required_quantity_base,
    inventory_quantity_base, open_order_quantity_base, shortage_quantity_base,
    planned_quantity_base, base_unit
)
VALUES
    ('SCHOOL-001', 'CANTEEN-001', 'PLAN-20260822-001', 'ING-CHICKEN', 28.8, 12, 10, 6.8, 6.8, 'kg'),
    ('SCHOOL-001', 'CANTEEN-001', 'PLAN-20260822-001', 'ING-PORK', 20.8, 26, 0, 0, 0, 'kg'),
    ('SCHOOL-001', 'CANTEEN-001', 'PLAN-20260822-001', 'ING-BEEF', 27.4, 11, 8, 8.4, 8.4, 'kg'),
    ('SCHOOL-001', 'CANTEEN-001', 'PLAN-20260822-001', 'ING-BROCCOLI', 18, 10, 6, 2, 2, 'kg'),
    ('SCHOOL-001', 'CANTEEN-001', 'PLAN-20260822-001', 'ING-POTATO', 37.1, 45, 0, 0, 0, 'kg'),
    ('SCHOOL-001', 'CANTEEN-001', 'PLAN-20260822-001', 'ING-TOMATO', 22.8, 12, 5, 5.8, 5.8, 'kg'),
    ('SCHOOL-001', 'CANTEEN-001', 'PLAN-20260822-001', 'ING-EGG', 220, 180, 30, 10, 10, 'count'),
    ('SCHOOL-001', 'CANTEEN-001', 'PLAN-20260822-001', 'ING-OIL', 8.24, 15, 0, 0, 0, 'L'),
    ('SCHOOL-001', 'CANTEEN-001', 'PLAN-20260822-001', 'ING-SOY', 1.6, 2, 0, 0, 0, 'L');

INSERT INTO alert_records (
    warn_id, source, third_warn_id, school_id, school_name, area_code, device_id, device_name,
    canteen_id, warn_happen_time, alarm_event_id, warn_content, status, process_status
)
VALUES
    ('WARN-INV-001', 'INVENTORY', 'INV-CHICKEN-LOW', 'SCHOOL-001', '智慧食堂研究学校', 'EAST', 'INV-SENSOR-001', '鸡肉库存监测', 'CANTEEN-001', '2026-08-21 10:00:00', 'EVENT-INV-001', '鸡肉库存低于明日午餐需求，预计缺口 6.8kg。', 'UNPROCESSED', 0),
    ('WARN-TEMP-001', 'IOT', 'TEMP-COLD-001', 'SCHOOL-001', '智慧食堂研究学校', 'EAST', 'COLD-001', '冷藏库温度传感器', 'CANTEEN-001', '2026-08-21 09:20:00', 'EVENT-TEMP-001', '冷藏库温度短时达到 8.6℃，需要复核。', 'UNPROCESSED', 0)
ON DUPLICATE KEY UPDATE
    warn_content = VALUES(warn_content), status = VALUES(status), process_status = VALUES(process_status),
    warn_happen_time = VALUES(warn_happen_time);

INSERT INTO compliance_records (
    school_id, canteen_id, record_id, category, subject_type, subject_id, subject_name,
    title, credential_no, valid_from, valid_to, attachment_refs_json, status, review_remark,
    version, reviewed_by, reviewed_at
)
VALUES (
    'SCHOOL-001', 'CANTEEN-001', 'CMP-CANTEEN-LICENSE', 'LICENSE', 'CANTEEN', 'CANTEEN-001', '一食堂',
    '食堂经营许可证', 'LIC-CANTEEN-001', '2026-01-01', '2026-12-31', '["study://license/CANTEEN-001"]',
    'APPROVED', '学习数据集已审核', 1, 'admin', '2026-01-02 10:00:00'
)
ON DUPLICATE KEY UPDATE
    valid_to = VALUES(valid_to), status = VALUES(status), review_remark = VALUES(review_remark),
    version = VALUES(version), reviewed_by = VALUES(reviewed_by), reviewed_at = VALUES(reviewed_at);

INSERT INTO operational_ledger_records (
    school_id, canteen_id, record_id, cycle_id, ledger_code, record_time, recorder_id,
    content_json, photos_json, status, remark
)
VALUES (
    'SCHOOL-001', 'CANTEEN-001', 'LEDGER-20260821-PURCHASE', 'CYCLE-001', 'PURCHASE_ACCEPTANCE',
    '2026-08-21 09:40:00', 'admin', '{"supplier":"SUP-001","result":"PASS","batchCount":9}', '[]',
    'COMPLETED', '学习数据集初始化记录'
)
ON DUPLICATE KEY UPDATE
    content_json = VALUES(content_json), status = VALUES(status), remark = VALUES(remark), record_time = VALUES(record_time);

COMMIT;
