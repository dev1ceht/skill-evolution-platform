# 接口对接提效基准方法

本基准用于回答“Frontend API Integration Skill 是否真实降低接口对接成本”。它不以演示任务或模型主观评分替代真实团队数据。

## 采样原则

每一行代表同一个接口任务在两种方式下的成对结果：传统开发与 Skill 驱动开发。两次执行必须使用相同 OpenAPI 版本、页面范围、代码仓库基线和完成定义。

- 至少收集 20 个 `sample_type=real` 的任务后，才判断 20× 声明；建议最终覆盖 20～50 个任务。
- `synthetic` 仅用于演示采集与报表链路，永远不计入声明门槛。
- `source_ref` 必须指向可追踪证据，例如任务单、提交、PR、录屏或计时记录。
- 任务顺序应交替或随机安排，避免先后顺序、熟悉度和人员差异系统性偏向某一组。
- 开始时间定义为拿到稳定契约并开始开发；结束时间定义为代码、类型检查、契约测试和页面验收全部通过。
- 缺陷使用统一观察窗口；返工只统计验收失败后需要重新修改的轮次。

## 输入模型

CSV 和 JSON 均受支持。CSV 模板见 `examples/benchmarks/synthetic-sample.csv`；JSON 使用同名字段组成的对象数组。

| 字段 | 说明 |
| --- | --- |
| `task_id` / `task_name` | 唯一任务编号与名称 |
| `project` / `framework` | 项目和 React/Vue 等技术栈 |
| `operation_count` | 本任务涉及的接口操作数 |
| `traditional_minutes` / `skill_minutes` | 两种方式达到同一完成定义的分钟数 |
| `*_rework_count` | 验收失败后的返工轮次 |
| `*_defect_count` | 统一观察窗口内确认的缺陷数 |
| `*_first_pass` | 是否首轮通过完整验收 |
| `skill_token_count` | Skill 流程消耗的 Token；未知时填 `0`，不要估算 |
| `sample_type` | `real` 或 `synthetic` |
| `recorded_at` | 带时区的 ISO 8601 时间 |
| `source_ref` | 可回溯的来源标识或链接 |

时长必须大于零；计数不能为负数；任务编号不能重复。输入文件的 SHA-256、所有来源引用和标准化任务记录会进入 JSON 报告。

## 计算口径

- P50/P90 使用排序后的线性插值分位数。
- `totalSpeedup = sum(traditional_minutes) / sum(skill_minutes)`，这是按总耗时加权的整体提速。
- `pairedSpeedup = traditional_minutes / skill_minutes`，报告其 P50/P90，避免少数大任务掩盖典型任务表现。
- 首轮通过率、平均返工和平均缺陷分别比较；Token 只统计 Skill 组。
- 只有真实任务数不少于 20，且真实任务的 `totalSpeedup >= 20` 时，报告才把 20× 状态标为 `supported`。
- 20 个真实任务与 20× 两个声明门槛固定在领域策略中，CLI 不允许下调。数据中只要存在真实任务，所有主指标都仅以真实任务计算；合成任务只保留在明细与来源追踪中。

## 生成报告

```powershell
python -m skill_evolution.cli benchmark `
  --input examples/benchmarks/synthetic-sample.csv `
  --name local-demo
```

命令固定写入项目根目录的 `outputs/benchmarks/local-demo.json` 和 `outputs/benchmarks/local-demo.html`。`--project-root` 用于在另一个受控项目根目录下运行；`--name` 只允许字母、数字、下划线和连字符，防止路径穿越。

示例数据的报告会显示 `insufficient-real-samples`，这是预期结果。真实数据应保存在受权限保护的数据源中；若包含人员或内部任务信息，不要直接提交到公开仓库。
