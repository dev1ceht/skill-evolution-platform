# 项目进度

> 记录当前执行状态与有效工作节点；普通查看、搜索和无状态变化的命令不写入。

## 当前任务

| 任务 | 状态 | 负责人 | 完成条件 | 证据 ID | 最后更新 |
|---|---|---|---|---|---|
| SC-004 | 已完成 | Codex | 员工/学生按今天或指定日期查询已发布菜单；已有 menuId 查询不回归 | E-SC004-TESTS、E-SC004-REVIEW | 2026-08-21 |

## 阻塞项

| 阻塞 | 影响 | 解除条件 | 状态 |
|---|---|---|---|
| 无已知阻塞 | 无 | 不适用 | 无 |

## 下一步

1. 进入 SC-005：库存与采购只读查询/确定性缺口分析。
2. 后续补充真实或本地 Mock AgentScope Provider 烟囱测试，并视需要抽取共享响应解析器。

## 进度历史

按时间倒序追加：日期、完成事项、证据 ID、遗留问题、下一步和确认来源。不要覆盖旧记录。

- 2026-08-21：完成 SC-001 领域边界、SOP 清单和 ADR；证据见 `docs/smart-canteen/`、`docs/adr/0001-use-generated-initial-data-for-study-environment.md`；下一步为 SC-003。
- 2026-08-21：完成 SC-002 study dataset、MySQL seed、重复导入验证和数据检查；证据见 `.project-to-act/tasks/SC-002/evidence/E-SC002-DATA.md`；下一步为 SC-003。
- 2026-08-21：完成 SC-003 AgentScope HarnessAgent 可选适配、三类角色映射、DINER 菜单只读权限和回归测试；证据见 `.project-to-act/tasks/SC-003/evidence/E-SC003-TESTS.md`、`E-SC003-REVIEW.md`；真实 Provider 烟囱测试和响应解析器抽取延期；下一步为 SC-004。
- 2026-08-21：启动 SC-004；范围收敛为员工/学生菜单只读查询，新增今天/指定日期查询与已发布过滤；下一步先补充失败测试。
- 2026-08-21：完成 SC-004；定向/全量 Java、Python、治理校验和双轴评审通过；证据见 `.project-to-act/tasks/SC-004/evidence/`；下一步为 SC-005。
