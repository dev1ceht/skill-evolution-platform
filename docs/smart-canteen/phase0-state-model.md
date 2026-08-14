# 阶段 0：核心状态模型

## Menu

| 当前状态 | 可迁移到 | 触发者 | 规则 |
| --- | --- | --- | --- |
| DRAFT | PENDING_APPROVAL | 食谱编辑者 | 菜品、餐次和份数完整 |
| PENDING_APPROVAL | APPROVED | 审批人 | 审批通过并记录意见 |
| PENDING_APPROVAL | REJECTED | 审批人 | 必须记录驳回原因 |
| REJECTED | DRAFT | 食谱编辑者 | 修改后重新提交 |
| APPROVED | PUBLISHED | 食堂管理员 | 发布前再次校验菜品和营养 |
| PUBLISHED | LOCKED | 系统/周期结束 | 发布数据进入不可变状态 |

禁止：`DRAFT → PUBLISHED`、`PUBLISHED → DRAFT`、直接删除已发布食谱。

## PurchaseOrder

| 当前状态 | 可迁移到 | 触发者 | 业务副作用 |
| --- | --- | --- | --- |
| DRAFT | SUBMITTED | 采购人员 | 锁定订单明细版本 |
| SUBMITTED | CONFIRMED | 供应商 | 记录供应商确认时间 |
| CONFIRMED | SHIPPED | 供应商 | 记录发货信息 |
| SHIPPED | DELIVERED | 供应商/食堂 | 记录送达时间 |
| DELIVERED | ACCEPTED | 验收人员 | 产生 Receipt 和 InventoryBatch |
| DRAFT/SUBMITTED | CANCELLED | 有权限人员 | 记录取消原因 |

禁止：已 ACCEPTED 订单回退；已产生验收库存后直接取消；供应商越权修改采购价格。

## InventoryBatch

```text
RECEIVED → AVAILABLE → PARTIAL_USED → EXHAUSTED
     └────→ QUARANTINED → RELEASED / DISPOSED
AVAILABLE ─────────────→ EXPIRED
```

- `RECEIVED` 表示验收记录已形成但尚未通过库存可用性校验。
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
- 验收入库：同一幂等键不能重复增加库存。
- 出库：同一幂等键不能重复扣减库存。
- 台账记录：同一周期和台账要求只能保留一份有效记录。
- 外部预警：`source + thirdWarnId` 是外部事件幂等键。
