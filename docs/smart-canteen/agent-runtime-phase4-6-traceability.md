# Agent Runtime 阶段 4–6 追踪证据

## 阶段 4：菜单领域闭环

| 要求 | 实现 | 验证 |
|---|---|---|
| `daily_menus` 作为唯一 Agent 写聚合 | `DailyMenu`、`DailyMenuService`、`JdbcOperationalStore`、Flyway V12；旧 `menus` 保留为兼容路径 | `DailyMenuApprovalModuleTest`、`OperationalCoreHttpTest` |
| 提交/审批/发布状态机和乐观版本 | `submitForApproval`、`recordDecision`、严格 `publish`、版本条件更新 | `DailyMenuApprovalModuleTest`、Agent 菜单 HTTP 测试 |
| 运行确认不替代领域审批 | `RUN_CONFIRM` 只推进 Run；`menu.record-decision` 独立调用菜单服务 | `AgentControllerHttpTest.menu_agent_separates_run_confirmation...` |
| 职责分离 | submitter 不能审批；submitter/approver 不能发布；审批/发布角色收紧 | `DailyMenuApprovalModuleTest`、现有操作 HTTP 回归 |
| 细粒度菜单权限 | Manifest 使用 `MENU_VALIDATE/MENU_SUBMIT/MENU_APPROVE/MENU_PUBLISH`，V15 写入角色权限映射，执行前重新加载当前权限 | `BusinessAuthorizationPolicyTest`、Agent HTTP 回归 |

## 阶段 5：可靠性和对账

| 要求 | 实现 | 验证 |
|---|---|---|
| Run Decision/Event 持久化 | `AgentRunDecision`、`AgentRunEvent`、决策幂等键与规范化请求 hash、JDBC Store、V11/V13/V14 表 | `AgentRuntimePersistenceTest`、Agent HTTP events |
| 幂等和并发版本 | actor/scope/idempotency 唯一键、决策请求 hash（version + decision + comment）、Run version 乐观更新、Step 业务键含 planHash | `AgentRuntimeTest`、`AgentControllerHttpTest`；真实 MySQL 门禁 `AgentRuntimeMySqlIntegrationTest` |
| 读重试、超时分类 | `AgentExecutionService` bounded read retry；读超时 `TIMED_OUT`，写超时 `RECONCILIATION_REQUIRED` | `AgentExecutionServiceTest` |
| 重启/人工恢复 | `resume` API、`markReconciliationRequired`、Step 对账检查点 | Agent Controller API 与 persistence 回归；真实 MySQL Spring 重启门禁 |
| 审计关联 | Agent plan/decision/execution 写入 `AuditStore`；异常会记录 `AUDIT_WRITE_FAILED` 事件并告警 | 全量 Spring 回归 |

## 阶段 6：契约和前端

| 要求 | 实现 | 验证 |
|---|---|---|
| 决策/取消/恢复/事件 API | `AgentController`、菜单领域状态查询与 OpenAPI 116 operation IR | `contracts/generated/api-ir.json`、Agent contract tests |
| 版本并发控制 | Request body `version`；RunView 返回当前 version | Agent HTTP tests |
| 前端计划确认和事件 | `AgentMenuApprovalWorkspace.vue` 读取领域状态、锁定输入并展示不可变业务参数、API port/client methods | `AgentMenuApprovalWorkspace.test.ts`、140 Vitest tests |

## 尚未宣称完成的生产门禁

真实 MySQL 8 并发/迁移/重启门禁已在 MySQL 8.4.11 隔离数据库实际通过；旧 `menus` 历史回填收口、分布式 claim lease、跨系统 outbox、敏感结果引用化和保留清理、clarification 编排、长期指标告警/保留、生产灰度配置，以及采购/库存/预警写 Skill 仍需独立验收。
