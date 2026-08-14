# 智慧食堂第五阶段需求追溯

本阶段把两份设计文档中的高频日常运营需求收敛为一个可部署的纵向切片。范围和延后项见 [`phase5-plan.json`](phase5-plan.json)；需求原文、章节和文档哈希见 [`phase5-requirements.yaml`](phase5-requirements.yaml)。

| 需求 | 实现位置 | 验收证据 | 状态 |
| --- | --- | --- | --- |
| AUTH-001 账号登录、刷新、注销、当前用户、PBKDF2/RBAC | `backend/src/main/java/com/example/smartcanteen/security/`、`AuthService`、`AuthController`、`V5__create_operational_core.sql` | `AuthModuleTest`：登录、`/me`、刷新轮换、旧 token 失效、注销、缺少认证拒绝 | 已完成 |
| CATALOG-001 食材、营养、菜品和配方 | `CatalogService`、`OperationalController`、`JdbcOperationalStore` | `OperationalCoreHttpTest`：范围内创建食材/菜品，单位和引用校验 | 已完成 |
| MENU-001 日食谱草稿、查询、发布、发布后不可修改 | `DailyMenuService`、`daily_menus`/`daily_menu_items` | `OperationalCoreHttpTest`：创建并发布；服务端状态规则拒绝已发布修改 | 已完成 |
| LEDGER-004 台账内容、照片、查询、统计、幂等 | `OperationalLedgerService`、`ledger/records`、`ledger/stats` | `OperationalCoreHttpTest`：周期配置、记录重复提交、统计只计一次 | 已完成 |
| PROCUREMENT-001 供应商、订单、状态、金额、收货 | `ProcurementOperationsService`、`JdbcOperationalStore`、`purchase_*` 表 | `OperationalCoreHttpTest`：金额服务端计算、状态迁移、订单幂等、收货幂等 | 已完成 |
| INVENTORY-001 批次库存、单位换算、预警、出库回滚 | `inventory_batches`、`inventory`、`stock_out_*`、`UnitConverter` | `OperationalCoreHttpTest`：入库、库存查询、溯源、库存不足和幂等键冲突 | 已完成 |
| DASHBOARD-001 首页摘要、风险因素、溯源 | `DashboardService`、`dashboard/*`、`traceability/{traceCode}`、`OperationsOverview.vue` | HTTP 测试 + `OperationsOverview.test.ts`：实时摘要、风险、加载/错误/空状态 | 已完成 |
| EXTERNAL-001 第三方/设备/通知端口边界 | `application/port/*Gateway`、现有预警中心、阶段计划 deferred 清单 | 契约保留归一化预警路径；没有厂商凭据不宣称已连接 | 按边界交付 |

## 关键不变量

- 新运营接口强制传 `schoolId` 与 `canteenId`；认证拦截器和服务端范围校验共同防止跨食堂读取。
- 采购总额由服务端根据明细计算；幂等键复用不同业务载荷会返回业务错误。
- 发布后的日食谱不可编辑；订单状态只允许显式迁移。
- 收货、库存变更、批次和溯源记录位于同一事务；库存不足时整笔出库回滚。
- refresh token 只保存 SHA-256 摘要，轮换或注销后旧 token 不能继续使用。
- Flyway V1-V4 不改写；V5 只追加运营表和库存阈值字段。
