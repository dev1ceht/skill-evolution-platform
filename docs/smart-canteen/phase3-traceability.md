# 第三阶段需求追溯

| 需求 | 实现 | 验证 | 状态 |
| --- | --- | --- | --- |
| DATA-002 | `CanteenScope`、Flyway `V3__scope_core_business_data.sql`、`JdbcSmartCanteenStore` 复合键查询 | `ScopedMenuApprovalModuleTest`、`ScopedProcurementPlanningModuleTest`、`ScopedInventoryReceivingModuleTest`、H2 重启测试 | 已实现 |
| ARCH-004 | 模块公开接口增加范围重载；范围对象留在 domain/application，JDBC 只负责 SQL | 模块测试使用范围 fake；Maven 编译和 Spring 上下文测试 | 已实现 |
| API-003 | 既有菜单、采购、库存 REST 路径增加可选 `schoolId`/`canteenId` 查询参数；缺省走默认兼容范围 | `ScopedCoreWorkflowHttpTest`、`SmartCanteenWorkflowHttpTest`、生成客户端契约测试 | 已实现 |
| TEST-003 | 阶段验证记录含命令、源提交、输入和环境 provenance | `phase3-verification.json` | 已实现 |

## 范围边界

本阶段只解决核心业务数据的学校/食堂隔离，不引入 RBAC、统一认证、Redis、RabbitMQ、设备接入、第三方采购平台或动态基础单位管理。未配置 MySQL 凭据时，真实 MySQL 验收继续作为环境门控项记录。
