# Canteen traceability rules

## Input

```yaml
scope: {school_id: "SCHOOL-001", canteen_id: "CANTEEN-001"}
trace_code: "TRACE-RECEIPT-001-L1"
facts:
  - {type: "menu", id: "MENU-001", trace_code: "TRACE-RECEIPT-001-L1"}
  - {type: "purchase_order", id: "PO-001", trace_code: "TRACE-RECEIPT-001-L1"}
  - {type: "receipt", id: "RECEIPT-001", trace_code: "TRACE-RECEIPT-001-L1"}
  - {type: "inventory_batch", id: "RECEIPT-001-L1", trace_code: "TRACE-RECEIPT-001-L1"}
  - {type: "stock_out", id: "OUT-001", trace_code: "TRACE-RECEIPT-001-L1"}
```

必需事实类型为 `menu`、`purchase_order`、`receipt`、`inventory_batch`、`stock_out`。事实 ID 必须存在且不能混用其他食堂范围。

## Chain order

```text
menu → purchase_order → receipt → inventory_batch → stock_out
```

缺少任何一环时状态为 `incomplete`。外部系统没有提供事实时记录缺口，不使用猜测数据填充。
