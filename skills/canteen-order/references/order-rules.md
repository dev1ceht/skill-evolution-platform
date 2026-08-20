# Canteen order rules

## Input document

采购计算文件使用 YAML 或 JSON，最小结构如下：

```yaml
scope: {school_id: "SCHOOL-001", canteen_id: "CANTEEN-001"}
period: "2026-W34"
status: "published"
meals:
  - date: "2026-08-20"
    meal: "lunch"
    servings: 100
    items:
      - {ingredient: "rice", quantity_per_serving: 0.12, unit: "kg"}
inventory:
  - {ingredient: "rice", quantity: 5, unit: "kg", safety_stock: 1}
product_mapping: {rice: "大米"}
```

`meals` 中的 `items` 也可以使用 `recipe` 作为字段名。每个食材必须有正数用量和单位；同一食材跨餐次会先聚合。

## Calculation

```text
采购缺口 = max(0, 菜单需求量 + 安全库存 - 可用库存)
菜单需求量 = Σ(就餐人数 × 每人用量)
```

脚本只把同一计量维度换算到基础单位：`kg → g`、`L → ml`。重量、体积和件数之间禁止自动换算；缺少商品映射的食材进入 `unmatched`，不能静默丢弃。

## Confirmation and submission

- 生成阶段只产生采购单文件，不产生外部副作用。
- `batch_order.py` 必须收到 `--confirm` 才能生成提交记录。
- 提交记录的幂等键由作用域、周期和采购明细计算；重复内容可重放，同键异内容必须失败。
- 没有外部平台合同时，提交记录的 `adapter_status` 为 `port-only`。
