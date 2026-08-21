# Procurement SOP

## Purpose

根据已发布菜单/食谱、库存和未入库订单生成可审计的采购申请 Draft，并在独立的运营流程中完成订单履约。

## Steps

1. 读取当前范围内的已发布菜单和食谱，按配方、菜单估算份数和基础单位计算需求；不由模型猜测数量，也不依赖未接入的真实预测服务。
2. 保存生成时的菜单、食谱、库存和未入库订单快照，创建状态为 `DRAFT` 的采购计划；助手入口必须先得到明确确认。
3. 允许运营人员人工调整后确认计划；本次助手切片不自动确认计划、不创建采购订单。
4. 将已确认计划转换为采购订单，金额由服务端根据明细计算。
5. 按草稿、提交、确认、收货、取消的规则迁移订单状态，并将收货交给库存入库 SOP。

## Evidence

- Requirement: `PROCUREMENT-001`。
- Implementation: `ProcurementPlanService`、`ProcurementOperationsService`、`purchase_*` 表。
- Verification: `OperationalCoreHttpTest`、`ProcurementPlanServiceModuleTest`。
