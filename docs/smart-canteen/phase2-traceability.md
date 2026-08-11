# 第二阶段需求追溯

| 需求 | 实现 | 验证 | 状态 |
| --- | --- | --- | --- |
| ARCH-002 | `MenuApproval`、`ProcurementPlanning`、`InventoryReceiving` 三个公共模块接口；`MenuStore`、`RecipeStore`、`InventoryStore` 三个持久化端口 | `MenuApprovalModuleTest`、`ProcurementPlanningModuleTest`、`InventoryReceivingModuleTest` | 已实现 |
| ARCH-003 | `JdbcSmartCanteenStore` 实现兼容聚合端口；`SmartCanteenWorkflow` 仅负责兼容转发和台账门面 | 模块测试替身只实现对应端口；Spring Boot 编译 | 已实现 |
| API-002 | Controller 路径不变；旧工作流方法改为调用模块；库存幂等键由 `InventoryReceivingService` 传给 `InventoryStore` | `SmartCanteenWorkflowHttpTest`、`LedgerCycleHttpTest` | 已实现 |
| TEST-002 | 依赖通过构造函数注入；模块测试不启动容器，HTTP 测试覆盖兼容路径 | Maven 全量测试 | 已实现 |

## 第二阶段边界

这一阶段解决核心业务代码的模块深度和测试接缝，不把“有 Docker 容器”当成 Redis/RabbitMQ 已接入。菜单、食谱、库存表的学校/食堂复合数据迁移、认证/RBAC、缓存、事件和外部平台接入进入后续阶段。
