---
name: canteen-inventory
description: 处理食堂收货、单位换算、库存批次和溯源码生成，并在入库前校验数量、批次和幂等信息。Use when用户要验收入库、登记供应商送货、换算食材单位、生成库存批次或检查重复收货时。
---

# Canteen Inventory

这个 Skill 面向收货和库存人员。它把一张收货单转换为可入库的批次记录，计算基础单位、校验幂等键，并生成后续溯源需要的批次标识。

## Workflow

1. 读取 [inventory-rules.md](references/inventory-rules.md)，确认采购订单、供应商、食堂范围和单位。
2. 运行 `scripts/receive_goods.py`，检查每行数量、批次、保质期和单位维度。
3. 展示批次、基础数量和溯源码，用户确认后再交给后端入库端口。
4. 没有真实后端或供应商接口时，只输出 `ready_to_post` / `port-only` 记录，不声称已经增加库存。

```powershell
python scripts/receive_goods.py receipt.yaml --output receipt-ready.yaml
```

## Business guardrails

- 收货必须绑定 `school_id + canteen_id`、采购订单和幂等键。
- `kg → g`、`L → ml` 可以换算；重量、体积和件数不能互换。
- 数量为零、批次缺失、单位不兼容或重复幂等键必须停止整单处理。
- 库存批次、收货事实和溯源码必须保持可追踪关联。
