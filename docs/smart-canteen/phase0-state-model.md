# 阶段 0：核心状态模型

## Menu

| 当前状态 | 可迁移到 | 触发者 | 规则 |
| --- | --- | --- | --- |
| DRAFT | PENDING_APPROVAL | 食谱编辑者 | 菜品、餐次和份数完整 |
| PENDING_APPROVAL | APPROVED | 审批人 | 审批通过并记录意见 |
| PENDING_APPROVAL | REJECTED | 审批人 | 必须记录驳回原因 |
| REJECTED | DRAFT | 食谱编辑者 | 修改后重新提交 |
| APPROVED | PUBLISHED | 食堂管理员 | 发布前再次校验菜品和营养 |
| PUBLISHED | —（可选归档标志，不是核心状态） | 系统/管理员 | 发布版本不可修改；需要变更必须新建版本 |

禁止：`DRAFT → PUBLISHED`、`PUBLISHED → DRAFT`、直接删除已发布食谱；不能通过“锁定”状态替代版本不可变规则。

## PurchaseOrder

| 当前状态 | 可迁移到 | 触发者 | 业务副作用 |
| --- | --- | --- | --- |
| DRAFT | SUBMITTED | 采购人员 | 锁定订单明细版本 |
| SUBMITTED | CONFIRMED | 供应商 | 记录供应商确认时间 |
| CONFIRMED | SHIPPED | 供应商 | 记录发货信息 |
| SHIPPED | DELIVERED | 供应商/食堂 | 记录送达时间 |
| DELIVERED | PARTIALLY_ACCEPTED / ACCEPTED / CANCELLED | 验收人员/有权限人员 | 结果由独立验收单确认；入库不在此状态迁移中隐式完成 |
| DRAFT/SUBMITTED | CANCELLED | 有权限人员 | 记录取消原因 |

禁止：已 ACCEPTED 或 PARTIALLY_ACCEPTED 订单回退；已产生库存后直接取消；供应商越权修改采购价格。

## Inspection / Receipt

```text
PENDING → INSPECTING → PASSED → STOCKED_IN
                    ├→ PARTIAL → STOCKED_IN
                    └→ REJECTED
```

- `DELIVERED` 只表示供应商送达，不表示食堂验收通过。
- `PASSED` 产生全部合格数量的入库明细；`PARTIAL` 同时记录合格和拒收数量。
- `REJECTED` 不产生可用库存；退货或补送必须关联原验收单。
- `PASSED` 将订单标记为 `ACCEPTED`，`PARTIAL` 将订单标记为 `PARTIALLY_ACCEPTED`；全量拒收后才允许按权限取消或等待补送。
- `STOCKED_IN` 表示入库动作已完成并已创建库存批次，验收和入库必须分别审计、分别幂等。

## InventoryBatch

```text
STOCKED_IN → AVAILABLE → PARTIAL_USED → EXHAUSTED
     └────→ QUARANTINED → RELEASED / DISPOSED
AVAILABLE ─────────────→ EXPIRED
```

- `STOCKED_IN` 只表示验收通过或部分接收的合格数量已经完成入库。
- `AVAILABLE` 才能参与正常出库。
- `PARTIAL_USED` 表示批次仍有余额。
- `QUARANTINED` 用于质量或资质异常，不能正常出库。
- `DISPOSED` 必须有报损原因、操作人和审计记录。

## LedgerCycle

```text
OPEN → CLEARED
OPEN → OVERDUE → CLEARED
```

- `OPEN` 表示至少有一项未完成台账。
- 周期开始时生成应完成项和当前预警。
- 完成单项只移除对应缺项。
- 全部完成后才进入 `CLEARED`。
- 截止时间后仍有缺项进入 `OVERDUE`，不能通过普通查询伪装为完成。

## Alert

```text
OPEN → PROCESSING → RESOLVED
OPEN → INVALID
```

- `OPEN`：已产生但尚未被责任人接收。
- `PROCESSING`：已查看或正在处置。
- `RESOLVED`：有完整处置结果和附件（如需要）。
- `INVALID`：经授权人员确认事件无效，必须记录原因。
- 相同来源和外部事件编号重复上送返回原记录；内容变化必须报冲突。

## 幂等边界

- 菜单发布：同一菜单版本重复发布返回当前结果。
- 采购订单：同一 `Idempotency-Key` 和相同载荷返回原订单；不同载荷报冲突。
- 验收和入库：同一验收/入库幂等键不能重复确认或增加库存。
- 出库：同一幂等键不能重复扣减库存。
- 台账记录：同一周期和台账要求只能保留一份有效记录。
- 外部预警：`source + thirdWarnId` 是外部事件幂等键。
