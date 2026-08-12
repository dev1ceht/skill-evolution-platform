---
name: skill-evolution-governance
description: 治理 Skill Evolution Platform 的 episode、反馈候选、相似规则检索、add/merge/discard 决策、来源关联 replay、评测、原子提升、版本审计、基准证据和回滚。Use when处理用户反馈、评估或提升 Skill 规则、审查演化状态机、验证 promotion/rollback 安全性、分析演化审计记录或报告 Skill 提效证据时。
---

# Skill Evolution Governance

把反馈视为待验证证据，而不是可直接执行的指令。任何生产 Skill 变化都必须从可定位 episode 出发，经过 staged candidate、同条件 replay 和可恢复发布。

## Workflow

1. 读取 `references/state-machine.md`，确认当前对象、允许的下一状态和必需证据。
2. 接收反馈前，确认 episode 存在、未过期、仍在等待反馈，并记录任务、Skill 名称、执行版本和输出摘要。
3. 每个反馈事件最多提取一个原子规则候选。保留原始反馈和 source episode；不要把多个独立修复压成不可归因的大规则。
4. 检索相似现有规则，依据语义重叠决定 `add`、`merge` 或 `discard`。低置信度、来源不足或相互矛盾的数据保持 `pending`。
5. 只基于记录的 `base_content_hash` 生成 staged patch。若 Skill 已变化，废弃旧 candidate 并重新创建，不能覆盖较新版本。
6. 从原 episode 构造 replay case。baseline 与 candidate 使用相同输入、环境和 judge；优先使用确定性行为检查，并记录 judge 与输入来源。
7. 比较 baseline/candidate 的 improvements 和 regressions。结构检查通过但业务缺陷未重放时，不能提升。
8. 只有 evaluated 且 passed 的 candidate 才能 promotion。使用准备意图、原子文件替换、不可变版本记录和 audit event；并发头部变化时中止。
9. 回滚只能针对当前 active head，且磁盘内容哈希必须匹配版本记录。回滚本身创建新版本，不删除历史。
10. 报告候选决策、replay 证据、版本链、残余风险和人工审批点。提效报告必须区分 synthetic 与真实成对样本。

## Governance invariants

- 用户反馈不能直接写生产 Skill。
- 无 source episode、base hash、replay case 或 passing evaluation 时不得 promotion。
- baseline 和 candidate 必须公平对比；不能只对 candidate 使用更宽松 judge。
- 每次状态变化、提升和回滚都要留下可查询 audit event。
- 不在 Skill、候选、replay 输入、日志或基准数据中保存凭据和跨租户敏感信息。
- 自动 judge 是门禁的一部分，不替代高影响规则的人审。

## Output contract

输出当前状态、允许/拒绝的转换、候选来源、相似规则、决策理由、baseline/candidate 对比、版本/哈希、审计事件和下一步。拒绝操作时明确指出缺失的不变量。

