# Agent business-write rollout v1

## Scope

The natural-language assistant now recognizes six confirmation-gated write intents:

| Domain | Intent | Canonical tool |
| --- | --- | --- |
| Procurement | `procurement.plan.generate` | `procurement.plan.generate` |
| Procurement | `procurement.order.create` | `procurement.order.create` |
| Procurement | `procurement.order.receive` | `procurement.order.receive` |
| Inventory | `inventory.receive` | `inventory.receive` |
| Inventory | `inventory.stock-out` | `inventory.stock-out` |
| Alert | `alert.dispose` | `alert.dispose` |

The resolver accepts explicit business coordinates only. Examples include:

- `生成采购计划 2026-08-18 至 2026-08-24`
- `创建采购订单，计划 PLAN-001，供应商 SUP-001，食材 ING-001 10 kg，单价 8`
- `采购订单 PO-001 收货 ING-001 10 kg，批次 BATCH-001，采购价 8`
- `库存入库 ING-001，供应商 SUP-001，2 kg，批次 BATCH-001，采购价 8`
- `库存出库 ING-001 2 kg，原因 午餐备料`
- `处置预警 WARN-001，说明 已整改`

Missing coordinates produce a clarification turn. A complete request creates a `WAITING_CONFIRMATION` Agent Run; only an explicit `确认` (or a domain-specific confirmation phrase) can move it to execution. `取消` cancels the plan.

## Safety gates

The write intents are active in the immutable Skill manifest so they can be inspected and planned, but execution fails closed unless all of these conditions hold:

1. `agent.write.enabled=true`;
2. `agent.write.allowed-scopes` contains the exact `SCHOOL/CANTEEN` pair;
3. `agent.write.allowed-intents` contains the exact intent;
4. the current principal still has the Skill role/permission and the scoped business permission;
5. the Agent Run is confirmed and the domain service accepts its idempotency key and transaction invariants.

High-risk domain duties remain separate from the Run confirmation: order conversion requires a
`CONFIRMED` procurement plan; stock-out requires a school/system approver role; alert disposal
requires a system administrator or regulator and an exact canteen-scoped alert. Manual inventory
receiving uses the canonical batch + inventory + traceability transaction and requires supplier,
batch and purchase-price coordinates.

The default configuration leaves the write pilot disabled and both allowlists empty. Menu publish keeps its existing assistant rollout and domain-approval gates.

For a one-canteen pilot, set all three values explicitly (never use a wildcard):

```text
SMART_CANTEEN_AGENT_WRITE_ENABLED=true
SMART_CANTEEN_AGENT_WRITE_ALLOWED_SCOPES=SCHOOL-001/CANTEEN-001
SMART_CANTEEN_AGENT_WRITE_ALLOWED_INTENTS=inventory.stock-out
```

## Production activation checklist

- Start with one canteen and one intent, normally `inventory.stock-out` after stock-balance reconciliation evidence is available.
- Set `agent.write.enabled=true`, then list only the pilot scope and intent; do not use wildcard values.
- Verify confirmation, cancellation, same-key replay, stale scope/permission rejection, and timeout-to-reconciliation metrics.
- Expand one intent at a time. Supplier and external alert gateways remain port-only until their contracts, credentials, timeout and failure semantics are approved.
