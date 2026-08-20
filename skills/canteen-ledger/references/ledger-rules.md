# Canteen ledger rules

## Input

```yaml
scope: {school_id: "SCHOOL-001", canteen_id: "CANTEEN-001"}
cycle_id: "2026-08-20"
required: ["MORNING_CHECK", "SAMPLE留样", "DISINFECTION"]
records:
  - {code: "MORNING_CHECK", submitted_by: "operator-1", submitted_at: "2026-08-20T08:00:00+08:00"}
```

`required` 是本周期的完整要求；`records` 中的 `code` 不在要求列表内时不能补齐周期。结果中的 `missing` 必须保持稳定排序，便于责任人逐项处理。

## Completion

```text
missing 非空 → INCOMPLETE / 保持预警
missing 为空 → COMPLETE + CLEARED / 允许清除当前周期预警
```
