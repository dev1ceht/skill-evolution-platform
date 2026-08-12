---
name: skill-evolution-benchmarking
description: 设计、采集、运行和审计 Skill Evolution Platform 的成对提效基准，区分真实与合成样本并保留来源、哈希和完成定义。Use when评估 Frontend API Integration 或其他 Skill 的速度、首轮通过率、返工、缺陷和 Token 效果，审查“20×”等效率声明，生成 benchmark JSON/HTML，或判断现有样本是否足以支持结论时。
---

# Skill Evolution Benchmarking

把基准当作可审计实验，不把演示数据或主观评分包装成生产提效证据。比较双方必须完成同一个任务定义。

## Workflow

1. 读取 `docs/benchmark-methodology.md` 和 `references/paired-study-checklist.md`，写明待验证声明、目标 Skill、适用任务和主要指标。
2. 定义稳定任务单元与完成条件：相同契约版本、页面/功能范围、仓库基线、测试门禁和缺陷观察窗口。
3. 设计成对比较。控制人员经验、工具、环境和任务顺序；交替或随机传统/Skill 方式，记录偏差和中断。
4. 采集原始字段，不回填估计值：任务 ID、技术栈、operation 数、两组时长、返工、缺陷、首轮通过、Skill token、样本类型、带时区时间和 source reference。
5. 在受权限保护的位置保存可能包含人员/内部任务信息的原始数据。报告只保留必要来源；输入文件记录 SHA-256。
6. 运行仓库 benchmark CLI，检查校验错误、真实/合成分组、P50/P90、总耗时加权提速、首轮通过率、返工、缺陷和 token。
7. 审计结论：存在真实样本时主指标只使用真实样本；synthetic 永远不计入声明门槛；样本不足时报告 `insufficient-real-samples`。
8. 检查异常值、任务难度不平衡、熟悉度/顺序效应、观察窗口差异和幸存者偏差。必要时报告分层结果，不删除不利样本。
9. 输出事实、置信边界与限制。相关性或一次内部实验不能被表述为普遍因果收益。

## Fixed integrity rules

- `traditional_minutes` 和 `skill_minutes` 必须大于零，计数不得为负，task ID 不得重复。
- `skill_token_count` 未知时填 `0` 并解释为未知，不估算。
- 只有至少 20 个真实任务且真实任务 `totalSpeedup >= 20` 时，20× 状态才可为 `supported`；CLI 不允许下调门槛。
- 不用模型主观 judge 替代完成条件、缺陷或时间证据。
- 不覆写旧报告来伪装新运行；名称、输入哈希和来源必须可追踪。

## Output contract

输出实验设计、原始数据质量问题、运行命令、输入哈希、JSON/HTML 报告、真实/合成样本数、指标、声明状态、偏差与限制，以及下一轮采样建议。

