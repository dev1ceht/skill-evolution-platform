# E-SC004-TESTS

## 范围

验证员工/学生菜单只读查询纵向切片：今天或指定日期解析、`menuId` 兼容、已发布过滤、Tool 参数边界、角色/范围回归，以及项目治理检查。

## 执行结果

| 检查 | 命令 | 结果 |
|---|---|---|
| Java 定向测试 | `mvn -q "-Dtest=RuleBasedAssistantIntentResolverTest,MenuToolExecutorTest,AssistantControllerHttpTest" test` | 通过 |
| Java 模型适配器边界测试 | `mvn -q "-Dtest=MenuToolExecutorTest,RuleBasedAssistantIntentResolverTest,AssistantControllerHttpTest,AgentScopeAssistantModelResolverTest,DeepSeekAssistantModelResolverTest" test` | 通过 |
| Java 全量回归 | `mvn -q test` | 退出码 0；Surefire 汇总 214 tests、0 failures、0 errors、2 skipped |
| Python/SOP/合约测试 | `python -m pytest -q --basetemp .pytest-tmp-sc004` | 20 passed |
| Project-to-Act 校验 | `python C:\Users\th\.agents\skills\project-to-act\scripts\init_project_management.py --project-root D:\project\smart-canteen --validate` | 通过；schema version 1 |
| Agent 生命周期校验 | `python C:\Users\th\.agents\skills\develop-ai-agents\scripts\manage_lifecycle.py --project-root D:\project\smart-canteen validate` | 通过；revision 4、stage 6 |
| Diff 格式检查 | `git diff --check` | 通过 |

## 验收覆盖

- `今天有什么菜？` 解析为按当前日期的 `menu.query`。
- `查询 2026-08-17 午餐菜单` 解析为 ISO 日期和 `LUNCH`。
- `menu.query` 支持 `menuId` 或 `menuDate`，缺失、非法日期、同时提供 ID/日期或 ID/餐次时给出确定性错误。
- HTTP 助手的日期查询只返回 `PUBLISHED` 菜单，草稿菜单不进入公开结果。
- 既有菜单发布与角色权限回归测试继续通过；本切片没有新增库存、采购或备餐写入。

## 环境说明

- 直接运行默认 `python -m pytest -q` 时，当前 Windows 环境的默认临时目录曾返回 `Permission denied`；改用工作区内 `.pytest-tmp-sc004` 后完整通过。该环境限制未修改业务代码。
- Java 测试日志出现既有测试夹具的 `Agent audit write failed` WARN，但没有对应失败用例；本证据不将其表述为“零告警”。
