# Agent Runtime 真实 MySQL 8 验收门禁

本门禁把 Agent Runtime 的关键并发与重启路径接入现有 MySQL 8 验收脚本。它不替代 H2 单元/HTTP 回归，也不宣称 claim lease 或生产调度已经完成。

## 执行方式

在 Docker Engine 可用、`infra/.env` 已配置且不含占位密码时运行：

```powershell
./infra/verify-stack.ps1
./infra/verify-mysql-workflow.ps1
```

脚本会创建带随机后缀的隔离数据库，执行 Flyway 迁移，运行下列环境门控测试，最后删除数据库：

- `SmartCanteenMySqlIntegrationTest`
- `AgentRuntimeMySqlIntegrationTest`

## Agent Runtime 验收内容

- 两个并发 `traceability.query` 启动请求使用同一 actor、scope 和幂等键时只产生一条 `agent_runs`；
- `agent_steps` 初始检查点与 `RUN_IDEMPOTENCY_REPLAY` 事件持久化；
- Agent Run 计划审计记录可在 `audit_logs` 落库，且审计 ID 遵守 64 字符约束；
- 同一个外层事务内重复启动同一幂等键时复用未提交的首个 Run，不误报不可恢复竞态；
- 外层会话事务回滚时不留下孤立的 Agent Run 或计划审计记录；
- 同一幂等键携带不同输入时拒绝；
- 关闭并重新启动 Spring 上下文后，Run、Step/Event 证据仍可读取；
- 迁移在真实 MySQL 8 上完成，且测试数据库不会污染默认业务库。

验证证据写入 `outputs/verification/smart-canteen-mysql-workflow-latest.json`，其中记录 MySQL 镜像、Flyway 版本、测试类和清单。

本轮已在 MySQL 8.4.11 隔离数据库上通过该门禁，数据库完成 schema migration V19（Flyway 11.20.3）后自动删除测试库；后续仍需在 CI 中持续执行以防止回归。
