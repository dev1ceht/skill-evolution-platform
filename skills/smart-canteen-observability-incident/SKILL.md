---
name: smart-canteen-observability-incident
description: 为智慧食堂关键业务链路设计日志、指标、追踪、健康检查和告警，并使用新鲜运行证据诊断、缓解和复盘故障。Use when新增关键工作流或外部集成、定义 Spring Actuator/业务指标、排查慢请求、错误率、数据不一致、消息积压、缓存异常、MySQL 故障，或编写 incident timeline、runbook 和 postmortem 时。
---

# Smart Canteen Observability and Incident

让系统状态可以从外部证据解释。可观测性围绕用户可见症状和业务不变量设计；故障诊断先建立时间线与证据，不先改代码猜原因。

## Instrumentation workflow

1. 读取 `references/signals-and-runbook.md`，定位关键用户旅程、依赖、失败模式和已有 Actuator/日志边界。
2. 为入口生成 correlation/request ID，并跨 HTTP、应用服务、数据库和 adapter 传播。记录 operation、结果、耗时、错误类别和必要的范围标识；范围值需最小化或脱敏。
3. 采用结构化日志。不要记录密码、token、完整请求体、手机号、设备敏感数据或模型上下文。
4. 为同步服务定义 RED 指标，为 MySQL/Redis/RabbitMQ 定义饱和、连接、延迟和积压指标，并增加少量业务完成/冲突指标。
5. 告警基于用户症状、持续时间和可操作阈值；每个告警链接到 owner、dashboard 和 runbook。容器健康不等于业务健康。
6. 限制 Actuator 和管理端点暴露范围；健康详情、配置和线程信息不得公开泄露。

## Incident workflow

1. 明确影响、开始时间、受影响学校/食堂或流程、当前严重度和最近变化。先保护证据。
2. 采集同一时间窗内的日志、指标、trace、部署、数据库/Flyway、队列/缓存和外部依赖状态，建立事实时间线。
3. 从用户症状向内缩小：入口 → 应用服务 → 数据库/中间件 → 外部 adapter。一次只验证一个可证伪假设。
4. 优先选择低风险缓解：停止有害流量、关闭 feature、回退兼容应用或隔离失败依赖。生产写入、数据修复和凭据变更需要明确授权。
5. 缓解后用业务请求和不变量验证恢复，继续观察错误率、积压和数据一致性；进程存活不是恢复证明。
6. 找到根因后添加最小回归测试和观测缺口修复，记录触发条件、贡献因素、检测/响应时间和防复发行动，不归咎个人。

## Output contract

观测设计输出 signal map、字段/指标、dashboard/alert/runbook 和敏感数据策略。事故诊断输出影响、时间线、证据、假设与排除、缓解、恢复验证、根因和有 owner 的行动项。

