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

新增或扩展：

- `ingredient_master`
- `ingredient_nutrition`
- `dishes`
- `dish_ingredients`
- `serving_profiles`
- `menus`
- `menu_items`
- `menu_approvals`
- `menu_versions`

将旧 `menus/recipe_requirements` 映射到规范 Menu/Dish/Recipe。无法自动映射的数据进入迁移异常表，不允许静默丢弃。

### V8：采购、验收、库存和溯源

新增或扩展：

- `procurement_plans`
- `procurement_plan_items`
- `supplier_catalog_items`
- `purchase_orders`
- `purchase_order_items`
- `deliveries`
- `inspections`
- `receipts`
- `inventory_batches`
- `stock_out_records`
- `traceability_records`

采购计划必须引用 Menu 版本；库存写入只能由 Receipt/StockOut 服务完成。

### V9：配置化台账和通知

新增：

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

