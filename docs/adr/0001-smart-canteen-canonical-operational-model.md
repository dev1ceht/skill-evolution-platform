---
status: accepted
---

# Smart Canteen canonical operational model

当前系统同时存在旧的 `menus/recipe_requirements` 审批模型和新的 `daily_menus/dishes/ingredients` 运营模型，导致已发布日食谱不能稳定驱动采购计划。我们决定以带审批状态的日食谱、菜品配方、采购计划、采购订单、验收入库、库存批次、领用出库和溯源码作为唯一的后续写模型；V1～V5 旧接口和旧数据只保留兼容读取或迁移入口，不再新增旧模型写入。所有运营聚合以 `schoolId + canteenId` 为边界，外部平台和设备只能通过适配端口进入归一化领域对象。

## Considered Options

- 继续维护两套模型：短期改动小，但会持续产生菜单、库存和采购数据不一致。
- 立即删除旧模型：破坏现有接口、测试和历史数据，无法安全发布。
- 采用新模型作为唯一写模型，旧模型通过兼容层过渡：迁移可分阶段完成，且可以保留现有用户和历史数据。

## Consequences

- 后续数据库迁移必须是前向兼容的，旧接口需要标记弃用并逐步转发到新应用服务。
- 采购计划必须引用菜单版本和菜单明细，不能只接受独立的菜单编号或旧配方数据。
- 验收、库存、出库和溯源必须在同一个食堂范围内建立关联。
- 二期设备和第三方平台不会直接污染核心领域对象，接入失败也不能改变核心业务状态。

## Sources

- 概要设计：3.4 食谱管理、3.6 采购管理、3.8 食品溯源信息、5.5 基础支撑层、5.6.2 授权控制。
- 二期设计：3.2.13 采购管理、3.2.14 食品安全溯源码、3.2.16 食谱管理、3.2.5 基础单位统一。
- 迁移边界和验收追踪见 [`docs/smart-canteen/phase0-migration-plan.md`](../smart-canteen/phase0-migration-plan.md) 与 [`docs/smart-canteen/phase0-traceability.md`](../smart-canteen/phase0-traceability.md)。
