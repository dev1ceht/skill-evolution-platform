# 智慧食堂真实业务接入与 Skill 自进化交接

## 结论

项目现在具备一个可运行、可测试、可审计的智慧食堂纵向业务切片，并已用真实接口生成缺陷推动 `frontend-api-integration` Skill 演化。该实现覆盖“Skill 编写/应用”和“Skill 自进化”两个实习产出点，但不应表述为完整智慧食堂生产系统。

## 已落地范围

| 环节 | 实现 | 验证边界 |
| --- | --- | --- |
| 文档与契约 | 6 个 OpenAPI 操作、API IR、页面任务计划 | Python 契约管线测试 |
| 菜单审批 | DRAFT → PENDING_APPROVAL → APPROVED/REJECTED | Java 领域测试 + MockMvc |
| 采购计划 | 菜谱需求减当前库存，仅输出正缺口 | Java 领域测试 |
| 单位换算 | kg/g、L/ml、count 统一为基础单位 | Java 领域测试 |
| 数据持久化 | MySQL + JDBC + Flyway，菜单、库存、入库幂等键和台账跨重启保留 | H2 文件库重启测试 + Spring 测试 |
| 库存入库 | `Idempotency-Key` 防重复，入库和幂等记录位于同一事务 | MockMvc 工作流测试 + 重启测试 |
| 台账预警 | 完成缺失台账后动态清除预警 | Java 领域测试 + MockMvc |
| 本地中间件 | MySQL、Redis、RabbitMQ 使用独立 Dockerfile 构建，由 Compose 编排并提供健康检查 | `docker compose config` 静态校验 + `verify-stack.ps1` 运行态验收入口 |
| Vue 页面 | Axios 适配、统一 envelope、loading/empty/error、审批交互 | Vitest + Vue Test Utils |
| 自动化接口测试 | 6 个生成式 fetch 契约测试 | Vitest + TypeScript 编译 |
| Skill 自进化 | pending episode、merge 候选、contract replay、审计与提升 | Python replay 测试 |

## 本次进化来源

真实契约第一次进入生成器时暴露三个关联问题：可复用 parameter `$ref` 没有展开；路径中的 `{menuId}` 没有编码替换；`Idempotency-Key` 被直接当作 TypeScript 参数名。它们被归并为一个 `merge` 候选，并由 `contract-replay-v1` 对完整智慧食堂契约离线回放。

离线判定以基线提交 `72d57ad...` 的不可变失败 artifact 和契约哈希为来源，要求候选同时满足：Skill frontmatter 不损坏、候选规则存在、无占位符、契约可生成、引用已解析、路径参数已编码、原始 Header 名被保留。实际 baseline/candidate checks、improvements、regressions 会写入 replay case、evaluation、audit event 和版本记录，规则随后进入 `SKILL.md`。

## 使用入口

- 示例总览：`examples/smart-canteen/README.md`
- OpenAPI：`examples/smart-canteen/contracts/smart-canteen.openapi.yaml`
- Spring Boot：`examples/smart-canteen/backend`
- Vue：`examples/smart-canteen/frontend`
- 回放证据：`examples/smart-canteen/replay/path-parameter-feedback.json`
- 演化后的 Skill：`skills/frontend-api-integration/SKILL.md`

## 明确边界

- 当前纵向切片已接入 MySQL；Redis 与 RabbitMQ 已完成容器化环境，但缓存和事件业务端口尚未接线。
- 数据库迁移目前只有初始版本与固定样例菜单，尚未覆盖生产数据导入、备份恢复和高可用方案。
- 明厨亮灶、设备告警、微信/统一认证和外部采购平台仅保留为后续端口，不包含真实凭据与硬件调用。
- “20 倍提效”尚无团队基线数据支持；当前只能提供自动生成覆盖率、测试数、构建时间和人工复核步骤，后续应按同一接口集记录传统耗时与 Skill 流程耗时再计算。
- 自动生成代码仍需人工审查；OpenAPI 是证据源，不替代真实响应、日志和业务方确认。

## 推荐下一迭代

1. 使用 Testcontainers 在 CI 中补充真实 MySQL 兼容性与并发幂等测试。
2. 为 Redis 缓存和 RabbitMQ 领域事件定义端口、失效策略、重试与死信语义后再接入业务。
3. 增加菜单分页 envelope、Excel 菜单导入 multipart 和旧版 action API 差异回放。
4. 将 replay runner 接入 CI，阻止未通过候选提升和破坏性契约变更合并。
5. 收集 5–10 次真实联调 episode，形成可比较的效率与质量指标。
