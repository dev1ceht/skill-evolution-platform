---
name: canteen-traceability
description: 根据菜单、采购订单、收货、库存批次和出库事实构建食堂食品溯源链路，并报告缺失环节。Use when用户要查询溯源码、追溯食材来源、核对供应商和收货批次或检查溯源链是否完整时。
---

# Canteen Traceability

这个 Skill 直接处理食品溯源查询。它组合业务事实形成可读链路，不凭前端展示或历史缓存补造缺失事实。

## Workflow

1. 读取 [traceability-rules.md](references/traceability-rules.md)，确认溯源码和事实类型。
2. 运行 `scripts/build_trace.py`，按固定顺序连接菜单、订单、收货、批次和出库事实。
3. 输出完整链路或 `missing_facts`；缺失外部设备、平台或供应商信息时明确标记，不声称链路完整。
4. 只在作用域和授权范围内展示供应商、批次和食品安全信息。

```powershell
python scripts/build_trace.py trace-facts.yaml --output trace-result.yaml
```

## Required facts

完整溯源至少需要 `menu`、`purchase_order`、`receipt`、`inventory_batch` 和 `stock_out` 五类事实。每类事实必须绑定同一个 `school_id + canteen_id` 和同一个 `trace_code`。

## Output

输出有序事实链、缺失环节、供应商和批次摘要、范围和 `complete` / `incomplete` 状态。
