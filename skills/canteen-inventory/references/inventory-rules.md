# Canteen inventory rules

## Receipt input

```yaml
scope: {school_id: "SCHOOL-001", canteen_id: "CANTEEN-001"}
receipt_id: "RECEIPT-001"
idempotency_key: "receive-PO-001-20260820"
purchase_order_id: "PO-001"
supplier_id: "SUPPLIER-001"
received_at: "2026-08-20T09:30:00+08:00"
lines:
  - {ingredient_id: "rice", quantity: 50, unit: "kg", batch_no: "RICE-0820", expires_on: "2026-09-20"}
```

每行必须有正数数量、批次号和有效期。脚本将重量换算为 g、体积换算为 ml，件数保持 pcs；不同维度拒绝换算。

## Idempotency

相同 `idempotency_key` 和相同收货内容可重放；相同键但收货内容不同必须标记冲突。批次 ID 与 `receipt_id + line` 关联，不能靠前端临时拼接溯源码。
