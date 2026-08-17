# Agent Run Claim Lease 基础切片

本轮交付的是数据库租约与 fencing 基础，不是完整的异步 worker 调度器。

## 已交付

- V20 `agent_run_claims` 表：每个 Run 同时只有一个 `owner_id + claim_token`；
- `AgentRunStore` 的 claim、续租、释放公共 seam；
- 不具备 claim capability 的 Store 对 claimed 写入 fail-closed，不会静默退回普通写入；
- MySQL `SELECT ... FOR UPDATE` 串行化同一 Run 的 claim；
- claim、续租和 fencing 校验统一读取数据库 `CURRENT_TIMESTAMP`，避免多实例 JVM 时钟来源混用；
- 租约过期后允许新 worker 接管；
- 旧 token 不能续租，持久化写入可通过 claim 校验拒绝旧 worker；
- claimed Run/Step/Event 写入口在同一事务内完成 fencing 校验与写入，并拒绝跨 Run 复用 token；
- H2 持久化测试与真实 MySQL 门禁覆盖并发单领取、释放、续租、过期接管和旧 token fencing。

历史 MySQL 门禁证据记录在 `outputs/verification/smart-canteen-mysql-workflow-latest.json`；本轮最终硬化后的重跑需要 Docker Desktop/CI MySQL 环境，本机因 Docker Engine 未启动未能再次执行。

## 当前边界

`AgentExecutionService` 仍采用现有同步事务执行模型，claim seam 尚未作为独立的异步调度循环启用。后续要完成生产级租约调度，还需要：

1. 将 claim、工具调用和结果落库拆成可恢复的事务边界；
2. 增加 heartbeat/自动续租和 stale Run 扫描；
3. 为旧 worker 增加更严格的 fencing 写入和外部副作用幂等；
4. 在真实 MySQL 多实例/进程重启场景持续验收。

因此当前可以宣称“具备数据库 claim lease 基础”，不能宣称“已完成分布式 Agent 调度”。
