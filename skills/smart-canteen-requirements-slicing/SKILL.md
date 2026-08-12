---
name: smart-canteen-requirements-slicing
description: 将智慧食堂设计文档、业务反馈和现有实现整理为可追溯需求目录，并规划可独立验收的纵向切片。Use when新增或变更菜单、食谱、采购、库存、台账、预警、设备、第三方平台等业务能力，或需要澄清 PDF 需求、编写阶段计划、定义验收标准、划定延期范围和生成需求追溯关系时。
---

# Smart Canteen Requirements Slicing

先建立需求证据和验收边界，再进入实现。把设计文档视为来源证据，把现有代码、数据库约束、OpenAPI 和真实响应视为实现约束；发现冲突时显式记录，不要静默选边。

## Workflow

1. 确定本次变更涉及的业务主体、操作者、学校/食堂范围、入口、预期结果和明确不做的内容。
2. 读取 `docs/smart-canteen/requirements.yaml`、相关阶段需求文件和原始设计文档的对应章节。需要创建或修改结构化需求时，先读 `references/artifact-contracts.md`。
3. 对照现有领域对象、公开接口、Flyway 迁移和测试，列出文档与实现之间的缺口、冲突和未知项。认证、权限、外部凭据、厂商协议或关键字段未知时，停止猜测并列出待确认项。
4. 为每条需求记录稳定 ID、来源文档与章节、单一可验证陈述、验收条件和 `approved/proposed/deprecated` 状态。运行现有需求校验脚本。
5. 选择最小纵向切片，使关键业务规则可以从公开入口一路验证到持久化或外部端口。列出 `included`、`deferred`、不变量、接口、迁移、测试和回滚边界。
6. 生成阶段计划和需求快照。若需求源可散列，记录源文件哈希；不要用后写的摘要替代原始证据。
7. 建立需求 → 实现 → 测试追溯表。`port-only`、环境受限或尚未接入的能力必须按实际状态标注。
8. 在实现前输出待确认问题、切片计划、风险和完成定义；只有已批准需求才能作为“已承诺范围”。

## Slice rules

- 业务切片优先围绕可观察结果，而不是按 controller/service/table 横向拆分。
- 每个写流程都要定义事务边界、幂等键、重试结果和并发冲突行为。
- 所有核心数据操作都要显式携带 `schoolId + canteenId` 或说明为何不适用。
- 外部平台和设备先定义端口与规范化模型；没有真实合同和凭据时，不伪造“已集成”。
- Redis/RabbitMQ 只有在缓存失效、消息幂等、重试、顺序和死信语义明确后才进入切片。

## Output contract

按范围产出：

- 结构化 requirements YAML 与来源快照
- 阶段计划 JSON，包含 included/deferred/invariants/tests/rollback
- OpenAPI 或外部端口变更清单
- 需求追溯 Markdown
- 未决问题、风险和明确的非目标

