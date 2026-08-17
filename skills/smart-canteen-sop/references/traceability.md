# Food traceability SOP

## Purpose

把食谱使用、采购履约、验收入库、批次库存和出库事实组合为可查询链路。

## Steps

1. 为入库批次、供应商、采购订单和食材保留稳定关联。
2. 出库时记录批次和使用事实，不通过前端或临时查询补造链路。
3. 按 `traceCode` 查询批次、供应商、入库、订单和食材信息。
4. 发现缺少外部设备或平台数据时返回明确缺口，不把 port-only Adapter 伪装成已接入。

## Evidence

- Requirement: `DASHBOARD-001` 及阶段 2/5 溯源闭环要求。
- Implementation: `DashboardService`、`traceability/{traceCode}`、批次和出库记录。
- Verification: `OperationalCoreHttpTest`、`OperationsOverview.test.ts`。
