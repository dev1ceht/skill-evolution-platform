# Smart Canteen 四阶段对齐说明

这份文件是按用户给出的交付顺序整理的当前基线，优先于早期把采购、库存和食堂范围混在一起的阶段草案。

| 阶段 | 正确目标 | 当前状态 | 关键证据 |
| --- | --- | --- | --- |
| 1 | 台账周期预警 | 已完成 | `LedgerMonitoring`、V2 迁移、周期 HTTP/重启/并发测试 |
| 2 | 菜单审批、食谱导入 | 已完成 | `MenuApproval`、`RecipeImport`、`RecipeImportModuleTest`、`RecipeImportHttpTest` |
| 3 | 采购、库存、统一单位 | 已完成 | `ProcurementPlanning`、`InventoryReceiving`、`UnitConverter`、多食堂采购 HTTP 验收 |
| 4 | 区县平台、明厨亮灶、晨检仪、预警中心 | 未开始 | 仅保留接口设计范围，未伪装成已接入 |

第二阶段新增的食谱导入行为是：只能给 `DRAFT` 菜单导入；导入会校验原料、数量和单位；同一菜单再次导入时整体替换旧食谱，避免重复行。审批后再次导入会返回统一业务错误。

第三阶段的统一单位行为是：入库请求先把 `kg/L` 转换为 `g/ml` 再写库存；采购计划把食谱需求转换到基础单位后与同一食堂库存比较；同一原料在库存中不能混用基础单位。采购和入库查询都带学校/食堂范围。

早期 `phase2-plan.json` 和 `phase3-plan.json` 是实现过程中的中间切片记录，保留作历史 provenance；本文件和 `four-phase-alignment.json` 是后续交付拆分的规范说明。
