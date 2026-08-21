# E-SC003-TESTS

日期：2026-08-21

任务：SC-003（AgentScope HarnessAgent 适配与智慧食堂角色上下文）

## 自动化检查

| 检查 | 结果 |
|---|---|
| `mvn -q test`（`backend/`） | 通过，退出码 0 |
| `mvn -q "-Dtest=AgentScopeAssistantModelResolverTest,AgentScopeProviderContextTest,AssistantRoleContextTest" test` | 通过，退出码 0 |
| `mvn -q "-Dtest=AssistantRoleContextTest,AssistantIntentResolverRouterTest,AgentScopeAssistantModelResolverTest,BusinessAuthorizationPolicyTest" test` | 通过，退出码 0 |
| `python -m pytest -q --basetemp=.pytest-tmp tests/test_smart_canteen_sop.py tests/test_business_skills.py` | 4 项通过，退出码 0 |
| `python skills/smart-canteen-sop/scripts/validate_sop_manifest.py --manifest docs/smart-canteen/sop-manifests.yaml --run sop-runs/menu-to-traceability.yaml` | 8 个 SOP、1 个组合运行通过，退出码 0 |
| Project-to-Act `--validate` | 通过，退出码 0 |
| `git diff --check` | 无实际空白错误；仅有 Git 换行转换提示 |

## 数据库启动验证

- 本地 MySQL 启动后，应用从 schema v27 迁移到 v28，Flyway 报告 28 个迁移全部成功。
- SQL 查询确认 `DINER` 角色及其 `MENU_READ` 权限存在，查询退出码为 0。
- 应用使用学习环境开关启动，随后由人工中断并完成优雅关闭；进程终止码不作为测试失败处理。

## 范围说明

- AgentScope 依赖已通过 Maven 编译与 Spring 上下文测试。
- 未配置真实模型密钥，因此没有执行外部模型调用；AgentScope provider 在无密钥时按设计失败关闭，默认规则解析路径保持可用。
- 本证据只覆盖 SC-003；菜单、库存、采购业务 Tool 接入属于 SC-004。
