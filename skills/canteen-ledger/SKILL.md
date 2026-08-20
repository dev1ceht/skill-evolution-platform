---
name: canteen-ledger
description: 检查食堂台账周期、汇总缺项并生成完成或预警结果。Use when用户要创建台账周期、提交台账记录、检查合规缺项、完成最后一项或确认是否可以清除预警时。
---

# Canteen Ledger

这个 Skill 直接处理食堂日常台账。它读取周期要求和已提交记录，报告缺项，判断周期是否完成，并生成可供后端或人工复核的结果。

## Workflow

1. 读取 [ledger-rules.md](references/ledger-rules.md)，确认周期、范围和台账编码。
2. 运行 `scripts/check_cycle.py`，按台账要求去重并计算缺项。
3. 如果缺项为空，向用户展示 `CLEARED` 结果；否则输出责任人需要补交的项目。
4. 保存周期快照和检查时间，不能用历史检查结果替代本次检查。

```powershell
python scripts/check_cycle.py ledger-cycle.yaml --output ledger-check.yaml
```

## Business rules

- 周期唯一键是 `school_id + canteen_id + cycle_id`。
- 只有属于当前周期的台账编码才能计为完成。
- 同一台账重复提交不产生额外完成项。
- 最后一项完成后才返回 `CLEARED`，并记录可清除的预警关联。
