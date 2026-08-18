# Agent Run Claim Lease 基础切片

本轮交付的是数据库租约、fencing 基础和一个可被调度器调用的 worker 执行 seam，不是完整的异步 worker 调度器。

## 已交付

- V20 `agent_run_claims` 表：每个 Run 同时只有一个 `owner_id + claim_token`；
- `AgentRunStore` 的 claim、续租、释放公共 seam；
- 不具备 claim capability 的 Store 对 claimed 写入 fail-closed，不会静默退回普通写入；
- MySQL `SELECT ... FOR UPDATE` 串行化同一 Run 的 claim；
- claim、续租和 fencing 校验统一读取数据库 `CURRENT_TIMESTAMP`，避免多实例 JVM 时钟来源混用；
- 租约过期后允许新 worker 接管；
- 旧 token 不能续租，持久化写入可通过 claim 校验拒绝旧 worker；
- claimed Run/Step/Event 写入口在同一事务内完成 fencing 校验与写入，并拒绝跨 Run 复用 token；
- `AgentRunWorker.claimAndExecute` 在无外层事务的入口中串联 claim、工具执行、claim-aware Run/Step/Event 检查点和 finally 释放；
- `AgentExecutionService.executeClaimed` 不再调用普通 `update/updateStep/appendEvent`，claim 丢失时直接抛出并禁止无 fencing 降级；
- worker 默认租约为 `PT30S`，可通过 `SMART_CANTEEN_AGENT_EXECUTION_LEASE` 配置；该值必须为正时长，且不替代后续 heartbeat 续租；
- claim 在终态检查点前丢失时，worker 只抛出 claim-loss 信号，不用旧 token 强行写 `RECONCILIATION_REQUIRED`；Run 可能暂留 `EXECUTING`，由后续 stale-run recovery/人工接管决定结果未知状态；
- H2 持久化测试与真实 MySQL 门禁覆盖并发单领取、释放、续租、过期接管和旧 token fencing。

MySQL 门禁证据记录在 `outputs/verification/smart-canteen-mysql-workflow-latest.json`；2026-08-18 已在隔离 MySQL 8.4.11 数据库重跑并通过 V20 迁移、并发 claim、过期接管和旧 token fencing，测试库已自动清理。

## 当前边界

同步 HTTP/助手调用仍使用既有 `AgentExecutionService.execute` 事务模型；`AgentRunWorker.claimAndExecute` 是可独立调用的 worker seam，但尚未由调度循环自动驱动。后续要完成生产级租约调度，还需要：

1. 增加 heartbeat/自动续租和 stale Run 扫描，并定义 worker 崩溃后的接管策略；
2. 为外部副作用补齐业务幂等、outbox/inbox 或显式对账；
3. 将 worker seam 接入单实例先行、可关闭的调度器，再做多实例灰度；
4. 在真实 MySQL 多实例/进程重启场景持续验收。

因此当前可以宣称“具备数据库 claim lease 基础和手动 worker 执行 seam”，不能宣称“已完成分布式 Agent 调度”。
