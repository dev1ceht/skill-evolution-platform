# Context Map

本仓库包含技能演化平台和智慧食堂示例两个相互独立的领域上下文。根级 [CONTEXT.md](./CONTEXT.md) 描述技能演化平台；智慧食堂业务语言位于 [examples/smart-canteen/CONTEXT.md](./examples/smart-canteen/CONTEXT.md)。

## Contexts

- [Skill Evolution Platform](./CONTEXT.md) — 管理 Skill、候选、评估、回放和版本提升。
- [Smart Canteen](./examples/smart-canteen/CONTEXT.md) — 管理学校食堂的食谱、采购、库存、台账、食安和监管数据。

## Relationships

- **Skill Evolution Platform → Smart Canteen**：平台提供需求切片、API契约、测试证据和阶段治理能力；不拥有食堂业务对象。
- **Smart Canteen → Skill Evolution Platform**：智慧食堂阶段计划和验收记录作为平台的工程产物保存；不把食堂业务数据写入平台领域模型。
