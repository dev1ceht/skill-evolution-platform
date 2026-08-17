# Procurement SOP

## Purpose

根据已发布食谱、库存和未入库订单生成可审计的采购承诺，并完成订单履约。

## Steps

1. 读取已发布食谱，按配方和基础单位计算需求。
2. 保存生成时的食谱、库存和未入库订单快照；允许人工调整后确认计划。
3. 将已确认计划转换为采购订单，金额由服务端根据明细计算。
4. 按草稿、提交、确认、收货、取消的规则迁移订单状态。
5. 将部分或完整收货交给库存入库 SOP；重复收货不重复增加库存。

## Evidence

- Requirement: `PROCUREMENT-001`。
- Implementation: `ProcurementPlanService`、`ProcurementOperationsService`、`purchase_*` 表。
- Verification: `OperationalCoreHttpTest`、`ProcurementPlanServiceModuleTest`。
