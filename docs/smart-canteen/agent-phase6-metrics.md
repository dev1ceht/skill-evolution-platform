# Agent Runtime 第六阶段：运行指标看板

本阶段新增一个范围受控的运行指标查询和前端看板，用于试点食堂的人工复盘与灰度验收。

## 查询边界

```text
GET /api/v1/agent/metrics
  ?schoolId=SCHOOL-PILOT
  &canteenId=CANTEEN-PILOT
  &from=2026-08-17T00:00:00Z
  &to=2026-08-18T00:00:00Z
```

- 仅接受显式 `schoolId + canteenId`；后端先执行范围校验，再要求 `SYSTEM_ADMIN`、`SCHOOL_ADMIN` 或 `REGULATOR` 角色。
- 未提供时间范围时默认最近 24 小时，单次窗口最长 31 天；不返回运行 ID、用户 ID、菜单 ID、原始输入或错误正文。
- 指标来自 `agent_runs`、`agent_steps`、`agent_run_events` 和 `audit_logs` 的聚合证据，查询结果不改变业务状态。

## 指标语义

| 指标 | 口径 |
| --- | --- |
| Run 总数与状态数 | 时间窗口内按创建时间归属当前食堂范围的 Run |
| 成功率 | `SUCCEEDED / totalRuns`；无 Run 时为 0 |
| 平均 Run/工具耗时 | 已结束 Run/Step 的 `created→updated`、`started→finished` 平均值 |
| 确认等待 | 当前 `WAITING_CONFIRMATION` 数量及已完成确认等待时长 |
| 幂等重放 | `RUN_IDEMPOTENCY_REPLAY` 事件数量；重放现在留下追加事件 |
| 对账/超时 | `RECONCILIATION_REQUIRED` 与 `TIMED_OUT` 终态数量 |
| 越权拒绝 | 当前食堂范围内的 `AGENT_AUTHORIZATION_DENIAL` 审计记录 |

指标标签保持有界，不使用用户、运行、菜单、食堂或请求正文作为 Micrometer 标签。越权拒绝同时写入当前食堂范围的持久化审计记录，并由无标签的进程级 Micrometer counter 作为写入失败时的运维信号；后者不混入范围聚合 API，且会在进程重启后归零。

## 回退与限制

- 看板只读；删除或关闭看板不会影响 Agent Run、菜单审批或原有业务页面。
- 关闭助手灰度开关仍只影响 `/api/v1/assistant/**`；Agent 指标接口是独立的运维读取边界。
- Run 总数按创建时间归属窗口；终态 Run 耗时按终态更新时间归属窗口；工具开始次数按 `started_at`，工具失败与耗时按 `finished_at` 归属窗口。
- 待确认数量是查询时仍为 `WAITING_CONFIRMATION` 且更新时间早于窗口终点的当前快照；确认等待时长会带入窗口起点之前最近一次状态事件，以覆盖跨窗口等待。
- 真实 MySQL 并发/迁移、分布式 claim lease、指标长期保留和外部监控告警仍需上线前独立验收。
