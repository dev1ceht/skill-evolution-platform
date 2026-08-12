---
name: smart-canteen-verification
description: 为智慧食堂及 Skill Evolution Platform 变更选择并执行分层测试、契约同步、真实 MySQL/中间件验收、回归检查和可审计证据采集。Use when需要验证功能、修复、迁移、OpenAPI 变更、前后端联调、Docker 基础设施、发布候选，或在声称完成前生成 verification.json 与需求追溯时。
---

# Smart Canteen Verification

完成声明必须基于本次运行的新证据。先运行最小相关检查，定位失败后再扩大范围；不要用历史 JSON、H2 成功或容器健康替代真实业务验收。

## Workflow

1. 根据需求 ID、阶段计划和当前 diff 列出变更面：Python 平台、Skill、OpenAPI/生成物、Vue、Spring domain/HTTP、Flyway、MySQL、Redis/RabbitMQ。
2. 读取 `references/test-matrix.md`，为每个受影响面选择最小确定性检查。先验证失败场景确实能被测试捕获，再验证实现。
3. 按从快到慢的顺序执行：静态/结构检查 → 单元/领域 → 契约/HTTP → 持久化重启 → 前端组件/构建 → 真实 MySQL/并发 → 中间件工作流。
4. OpenAPI 变更后重新生成 API IR、TypeScript client 和 contract tests，并检查提交的生成物没有漂移。
5. 数据库变更同时验证空库全迁移、当前版本升级、H2 MySQL 模式和真实 MySQL。已发布 Flyway 文件不得修改。
6. 运行环境受限时，将检查标记为 `skipped` 或 `environment-gated`，列出缺失前提；不得推断为通过。
7. 生成新 verification artifact，记录输入、源提交或工作树状态、环境版本、精确命令、退出结果、证据、已知限制和清理结果。先删除或隔离旧 evidence，防止误归因。
8. 更新 Requirement → Implementation → Verification 追溯。状态只允许反映现有证据。
9. 给出门禁结论：`pass`、`blocked` 或 `conditional`，并附回滚/恢复方案。任何必需检查失败都不能宣称完成。

## Evidence rules

- 记录命令和可复核摘要，不伪造未运行的时间、版本、测试数或镜像摘要。
- H2 用于快速反馈；涉及事务、索引、并发和 MySQL 方言时必须补真实 MySQL。
- 容器 `healthy` 只证明进程就绪；只有业务请求/消费/缓存行为通过才证明集成可用。
- synthetic benchmark 必须继续标记为 synthetic，不能作为真实提效结论。
- 测试数据库、临时凭据和 Compose 资源必须记录清理结果；不把秘密写入 evidence。

## Output contract

输出执行矩阵、失败诊断、fresh verification JSON、追溯更新、残余风险、环境门禁和最终发布结论。

