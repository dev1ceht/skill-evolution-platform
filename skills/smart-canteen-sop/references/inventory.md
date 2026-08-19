# Inventory SOP

## Purpose

以食材基础单位和批次事实管理入库、库存预警、出库和食品溯源节点。

## Steps

1. 校验食材基础单位和请求单位，先完成 kg/L 到 g/ml 等基础单位换算。
2. 在收货事务内创建批次、增加库存并记录幂等键。
3. 查询库存余额和阈值，低于阈值返回 `warning=true`。
4. 出库前校验单位兼容和库存余额；失败时整笔事务回滚。
5. 将批次、订单、供应商和出库事实交给溯源查询。

## Evidence

- Requirement: `INVENTORY-001`。
- Implementation: `ProcurementOperationsService`、`UnitConverter`、`inventory_batches`、`stock_out_*`。
- Verification: `OperationalCoreHttpTest`、`OperationsToolExecutorTest`、MySQL workflow evidence。
