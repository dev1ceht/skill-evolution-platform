---
name: canteen-order
description: 根据食谱、就餐人数、库存和食材映射生成可确认的食堂采购单，并准备批量下单记录。Use when用户要计算采购量、匹配供应商品名、生成采购表、处理未匹配食材或提交一批采购订单时。
---

# Canteen Order

这是面向食堂采购人员的业务 Skill，不是 API 设计或代码生成工具。它把已发布菜单转换成采购需求，保留计算依据，并在真正下单前要求人工确认。

## Generate the order sheet

1. 读取菜单/食谱文件、就餐人数、库存快照和可选的商品映射；输入格式与单位规则见 [order-rules.md](references/order-rules.md)。
2. 运行 `scripts/generate_order.py`，按“需求量 + 安全库存 - 可用库存”计算缺口。
3. 对没有商品映射的食材停止下单，逐项向用户展示候选或请求确认；确认后的映射保存到映射文件供下次复用。
4. 将生成结果交给用户确认。`ready_for_confirmation` 不是已经下单，不能跳过确认直接提交。

```powershell
python scripts/generate_order.py menu.yaml --inventory inventory.yaml `
  --mapping product-mapping.yaml --output order-sheet.yaml
```

## Batch order

只有用户明确确认采购单后，才能运行 `scripts/batch_order.py`：

```powershell
python scripts/batch_order.py order-sheet.yaml --confirm --output submission.yaml
```

脚本会生成稳定幂等键和批量提交记录。没有真实供应商平台合同、凭据或网络配置时，结果必须标记为 `port-only`，不能声称已向外部平台下单。

## Business guardrails

- 只处理同一个 `school_id + canteen_id` 范围内的菜单、库存和订单。
- 只消费已发布菜单；草稿、待审批菜单不能生成正式采购单。
- 单位维度不兼容时停止计算，不使用猜测的换算比例。
- 同一幂等键重试返回原提交记录；同键异内容必须拒绝。
- 采购单、映射确认和提交结果都要保留输入快照与输出路径。

## Output

输出采购明细、未匹配食材、库存扣减依据、确认状态、幂等键和 `ready_for_confirmation` / `port-only` / `submitted` 等明确状态。
