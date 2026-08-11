---
name: smart-canteen-backend
description: Use the智慧食堂设计文档、结构化需求目录和现有 Spring Boot 后端仓库，设计并实现可追溯的后端业务切片、数据库迁移、OpenAPI、领域规则、集成适配器和验证测试。Use when implementing or reviewing menu approval, ledger cycles, procurement, inventory, alarms, third-party integrations, or backend changes in this smart-canteen project.
---

# Smart Canteen Backend

把智慧食堂设计文档变成可以审查、实现和验证的 Java 后端变更。设计文档是需求证据；现有代码、数据库和真实接口响应是实现约束。不要把 PDF 中的示例字段、旧接口或截图文字未经确认直接当成生产契约。

## Workflow

1. 读取 `docs/smart-canteen/requirements.yaml`，并定位需求对应的原始文档、章节和现有实现。
2. 明确本次变更的业务模块、数据范围（学校/食堂）、状态迁移、事务边界、幂等语义和外部依赖。发现文档矛盾、缺少字段或权限规则时停止猜测并列出问题。
3. 先输出后端变更计划：需求追溯、领域对象、公开接口、数据库迁移、消息/缓存影响、测试矩阵和回滚方案。
4. 在模块公开接口处实现业务规则。优先使用模块化单体；外部平台、设备、对象存储、通知和消息队列通过显式 Adapter 接入。
5. 更新 Flyway 迁移和 OpenAPI。迁移必须可从空库执行，也必须能从当前版本升级；不得修改已发布的迁移文件。
6. 先运行领域和 HTTP 测试，再运行 H2 文件库重启测试、MySQL 集成测试和最小构建。每个结果都记录命令、版本、输入快照和失败原因。
7. 生成需求追溯报告：每条需求对应代码、迁移、接口和测试；未实现项必须明确标记，不得用“已支持”掩盖占位实现。
8. 用户反馈只生成 staged candidate。候选必须包含来源 episode、需求 ID、影响模块、回放场景和验证结果，通过审核后才提升 Skill 版本。

## Backend change contract

输出应至少包含：

- `requirements.snapshot.yaml`：本次使用的需求和原始来源哈希
- `backend-plan.json`：模块、接口、状态、事务、迁移和测试计划
- Spring domain/application/infrastructure 变更
- Flyway migration 和迁移说明
- OpenAPI 更新及兼容性报告
- 领域、HTTP、持久化、并发或集成测试
- `verification.json`：命令、结果、数据库/镜像版本和未决风险
- `traceability.md`：需求 → 实现 → 测试的映射

## Guardrails

- 设计文档是证据，不是无条件真相；必须优先核对现有代码、真实响应和数据库约束。
- 所有核心写操作都要说明事务边界、并发策略和重复请求的结果。
- 学校和食堂是数据隔离维度；没有明确数据范围不能实现查询或写入。
- 不把密码、token、手机号或第三方真实凭据写进 Skill、OpenAPI、测试 fixture 或日志。
- 不把 Redis/RabbitMQ 仅作为容器装饰；接入前必须定义缓存失效、消息幂等、重试和死信语义。
- 不直接修改生产数据或历史 Flyway 文件；先生成 staged change 并审查 diff。
- 自动生成代码仍需人工确认业务规则、权限、错误码、SQL 索引和外部系统协议。

## First vertical slice: ledger alerts

台账预警模块的公开接口只有三类：开始周期、完成台账、查询当前预警。内部实现负责周期初始化、缺项聚合、幂等完成、预警状态持久化和恢复。完成最后一项后状态必须为 `CLEARED`；重复完成已完成项目必须返回相同结果；未知编码必须失败。

## Learned rules

- 台账预警按复合身份 `school_id + canteen_id + cycle_id` 隔离；周期、要求和预警表的主键/外键都必须带上这三个维度，不允许使用全局 `completed` 标记。
- `ledger_alerts` 是可审计状态，而不是只在内存中由缺项集合临时计算。
- 接口契约中的枚举和错误行为必须与领域对象、数据库约束和测试保持一致。
