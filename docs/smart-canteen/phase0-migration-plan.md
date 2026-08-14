# 阶段 0：数据库与兼容迁移路线

## 迁移原则

- V1～V5 保持不变，后续只添加 V6 及以上迁移。
- 先扩展结构，再双读校验，最后切换唯一写入口。
- 每次迁移都必须能在空库和已有 V5 数据库上执行。
- 不在迁移脚本中删除历史业务数据。
- 旧接口保留兼容期，响应增加弃用说明，最终再移除。

## 建议迁移序列

### V6：组织、人员和权限

新增或完善：

- `schools`
- `canteens`
- `app_users`
- `roles`
- `permissions`
- `user_roles`
- `role_permissions`
- `staff`
- `audit_logs`

回填当前 `app_users.role` 到角色关联表，保留旧字段作为兼容读取字段，待切换完成后再废弃。

### V7：规范食谱模型

V5 已经提供 `ingredients`、`dishes`、`dish_ingredients`、`daily_menus` 和
`daily_menu_items`，V7 必须复用并扩展这些表，不能再次创建同名表。建议扩展：

- 食材营养和单位换算字段；
- 菜品适用人群、营养计算和配方版本字段；
- 日食谱的审批状态、发布时间、不可变版本和带量明细字段；
- `menu_approvals`、`menu_versions` 等审批/版本关联表（确认 V5 中不存在后再创建）。

将 V3 的旧 `menus/recipe_requirements` 映射到现有 `daily_menus`、`daily_menu_items`、
`dishes` 和 `dish_ingredients`。无法自动映射的数据进入迁移异常表，不允许静默丢弃。

### V8：采购、验收、库存和溯源

V5 已经提供采购订单、验收、库存批次、出库和溯源的基础表，包括
`purchase_orders`、`purchase_order_items`、`purchase_receipts`、
`purchase_receipt_items`、`inventory_batches`、`stock_out_records`、
`stock_out_items` 和 `traceability_records`。V8 复用并扩展这些表，不能创建第二套订单或库存表。

新增或扩展的重点是：

- `procurement_plans`、`procurement_plan_items` 和 `supplier_catalog_items`；
- `deliveries`、`inspections` 以及验收附件、拒收/部分接收明细；
- 订单、验收、入库、库存批次和溯源之间的外键、幂等键及审计字段。

采购计划必须引用 Menu 版本；库存写入只能由 Receipt/StockOut 服务完成。

### V9：配置化台账和通知

V2 已经提供 `ledger_cycles`、`ledger_cycle_requirements` 和 `ledger_alerts` 等周期台账基础结构，
V5 也已存在运营台账表。V9 复用这些表并补齐配置化能力，新增：

- `ledger_types`
- `ledger_templates`
- `ledger_configurations`
- `ledger_requirements`
- `ledger_records`
- `notification_rules`
- `notifications`
- `job_runs`

将当前固定 `LedgerCode` 映射为初始化字典数据，保留旧编码兼容查询。

### V10：食品安全和预警规则

新增：

- `licenses`
- `health_certificates`
- `management_documents`
- `waste_recyclers`
- `supplier_qualifications`
- `alert_rules`
- `alert_disposals`
- `alert_notifications`

当前 `alert_records` 继续作为统一事件表，但补齐规则、通知和处置过程关联。

### V11：统计和监管快照

新增：

- `purchase_statistics`
- `inventory_statistics`
- `risk_assessments`
- `normalization_ratings`
- `data_sync_runs`
- `city_data_snapshots`
- `inspection_tasks`

统计快照必须保留统计周期、计算版本和来源时间，避免报表结果无法复现。

### V12：设备和外部接入

新增：

- `integration_connections`
- `devices`
- `device_sync_logs`
- `external_raw_messages`
- `device_person_bindings`

设备数据必须先落原始报文和同步日志，再转为内部业务记录。

## 兼容策略

| 现有能力 | 后续处理 |
| --- | --- |
| `/api/v1/menus/{id}/submit` | 保留，转发到规范 Menu 服务 |
| `/api/v1/menu-approvals/{id}/decision` | 保留，转发到规范审批服务 |
| `/api/v1/procurement-plans/generate` | 改为读取规范 Menu 版本 |
| `/api/v1/inventory/receipts` | 保留为兼容入口，最终统一到 Receipt 服务 |
| `/api/v1/ledger-cycles` | 保留查询，新增配置化台账服务作为唯一写入口 |
| `/api/v1/alerts` | 保留，扩展规则、通知和处置过程 |
