# Agent Run Claim Lease 基础切片

本轮交付的是数据库租约、fencing 基础、可续租的 worker 执行 seam、claim-aware stale-run 恢复和一个默认关闭的异步轮询器；它仍不是已完成生产灰度的多实例调度平台。

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
- worker 执行期间由独立 heartbeat 线程按可配置间隔续租，heartbeat 失败不绕过 fenced checkpoint；
- `findStaleExecuting(cutoff, limit)` 只返回 `EXECUTING` 且 Run 时间戳过期、同时不存在有效 claim 的 Run；
- 恢复真正写入前，`confirmStaleExecution` 在同一事务按 Run → Claim 加锁并复核版本、状态和数据库时间；心跳先获锁则恢复跳过，恢复先获锁则旧心跳被 fencing；
- `AgentRunRecoveryService` 将这类结果转为 `RECONCILIATION_REQUIRED`，使用独立事务和内部恢复上下文，并在 Store 不具备 claim capability 时 fail-closed；
- stale recovery 使用 `agent-recovery-<runId>-v<version>` 幂等键，并将标记写入 Run Event/Audit 证据；expected version 作为重复恢复的状态转换 fence；
- `AgentRunScheduler` 以默认关闭的 `@Scheduled` 轮询驱动已有 `PLANNED` Run，按 Run 快照重建 Skill、操作者和食堂范围上下文；单个 claim 冲突或坏 Run 不阻塞同批后续 Run；
- 调度器和恢复器共享 `SMART_CANTEEN_AGENT_SCHEDULER_ENABLED` 开关；启用前必须配置唯一 `SMART_CANTEEN_AGENT_SCHEDULER_OWNER_ID`、非空 `SMART_CANTEEN_AGENT_SCHEDULER_ALLOWED_SCOPES` 白名单；白名单由 Store 查询在 `LIMIT` 前过滤，避免非试点 Run 长期占满批次；并完成生产灰度/kill-switch 证据；
- `AgentExecutionService.executeClaimed` 不再调用普通 `update/updateStep/appendEvent`，claim 丢失时直接抛出并禁止无 fencing 降级；
- worker 默认租约为 `PT30S`，可通过 `SMART_CANTEEN_AGENT_EXECUTION_LEASE` 配置；heartbeat 默认 `PT10S`，可通过 `SMART_CANTEEN_AGENT_EXECUTION_HEARTBEAT` 配置且必须短于租约；
- claim 在终态检查点前丢失时，worker 只抛出 claim-loss 信号，不用旧 token 强行写 `RECONCILIATION_REQUIRED`；Run 可能暂留 `EXECUTING`，由后续 stale-run recovery/人工接管决定结果未知状态；
- H2 持久化测试与真实 MySQL 门禁覆盖并发单领取、释放、续租、过期接管和旧 token fencing。

MySQL 门禁证据记录在 `outputs/verification/smart-canteen-mysql-workflow-latest.json`；2026-08-18 已在隔离 MySQL 8.4.11 数据库重跑并通过 V20 迁移、并发 claim、过期接管和旧 token fencing，测试库已自动清理。

## 当前边界

同步 HTTP/助手调用仍使用既有 `AgentExecutionService.execute` 事务模型；异步 seam 已由默认关闭的单实例轮询器和 stale-run 恢复器接通，但没有默认启用。后续要完成生产级租约调度，还需要：

1. 为外部副作用补齐业务幂等、outbox/inbox 或显式对账；
2. 在真实 MySQL 多实例/进程重启场景持续验收 heartbeat、恢复和 owner-id 隔离；
3. 以单实例先行、可关闭的配置完成生产灰度和 kill switch 演练；
4. 增加敏感结果引用化、保留/清理作业和长期监控告警。

因此当前可以宣称“具备数据库 claim lease 基础、heartbeat、stale-run 对账恢复和默认关闭的单实例 worker 调度 seam”，不能宣称“已完成生产级多实例 Agent 调度”。
