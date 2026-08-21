# E-SC008-TESTS

## 范围

验证采购申请 Draft 的自然语言入口、日期澄清、预览确认、确定性 BOM/库存/在途计算、权限、幂等和“只生成 DRAFT、不创建采购订单”边界。真实预测服务、天气/预约接入和采购订单提交不在本轮范围。

## 自动化命令

| 命令 | 结果 |
|---|---|
| `mvn -q -Dtest=RuleBasedAssistantIntentResolverTest,AssistantControllerHttpTest,BusinessAuthorizationPolicyTest,OperationsToolExecutorTest,ProcurementPlanServiceModuleTest test` | 通过 |
| `mvn -q -Dtest=RuleBasedAssistantIntentResolverTest test`（最终测试断言整理后） | 通过 |
| `mvn -q test` | 通过：270 tests，0 failures，0 errors，2 skipped，72 reports |
| `python -m pytest -q --basetemp .pytest-tmp-sc008` | 通过：20 tests |
| `python skills/smart-canteen-sop/scripts/validate_sop_manifest.py --manifest docs/smart-canteen/sop-manifests.yaml --run sop-runs/menu-to-traceability.yaml` | 通过：12 SOPs、1 composition |
| `python C:\Users\th\.agents\skills\project-to-act\scripts\init_project_management.py --project-root D:\project\smart-canteen --validate` | 通过：`valid=true`、`issues=[]` |
| `python C:\Users\th\.agents\skills\develop-ai-agents\scripts\manage_lifecycle.py --project-root D:\project\smart-canteen validate` | 通过：revision 9、projectStatus completed |
| `git diff --check` | 通过 |

全量 Maven 日志中仍有既有 H2 学习测试的 `Agent audit write failed` WARN；测试进程退出码为 0，未产生失败或错误。

## HTTP 行为证据

- `帮我生成 2026-08-22 的采购申请草稿` 返回 `CONFIRMATION_REQUIRED`，Run 为 `WAITING_CONFIRMATION`，预览只返回业务参数，不创建采购计划。
- 回复 `确认` 后调用既有 `procurement.plan.generate`，返回 `DRAFT`；示例中理论需求为 `20000g`、库存为 `1000g`、缺口为 `19000g`。
- 相同确认幂等键重放返回同一 Turn，采购计划只增加一条；相同幂等键提交不同消息返回 400；`purchase_orders` 数量保持为 0。
- 缺少日期时保存持久化澄清、不启动 Agent Run；随后发送 `2026-08-22` 可恢复为 `WAITING_CONFIRMATION`。
- `采购 Draft 2026-08-22` 可直接识别；`查看采购 Draft ...`、`不要生成采购申请草稿 ...` 不会进入写入意图；非法日期 `2026-02-31` 在预览前进入有效日期澄清。
- `PROCUREMENT_PLAN_WRITE` 由服务端权限策略控制，DINER 无权生成 Draft；业务服务继续使用服务端 `ExecutionContext/CanteenScope`。

## 交付边界

本轮复用了既有 `ProcurementPlanService` 和 Tool，没有新增真实预测服务、预测算法、MCP 集群、SubAgent、采购订单创建或数据库迁移。
