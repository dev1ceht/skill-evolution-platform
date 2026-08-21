# E-SC011-TESTS

学习环境模拟支付切片的验证证据。所有支付动作均限定为 `STUDY_MOCK`，不代表真实支付网关或结算。

| Check | Result |
|---|---|
| Maven 定向回归：`AssistantControllerHttpTest,MealOrderControllerHttpTest,MealOrderToolExecutorTest,RuleBasedAssistantIntentResolverTest` | 通过；83 项测试，0 failures，0 errors |
| Maven 全量：`mvn -q test` | 通过；测试报告汇总 301 项，0 failures，0 errors，2 skipped；Flyway 校验并执行至 V41 |
| Frontend Vitest：`npm run test` | 通过；12 个测试文件、149 项测试 |
| Frontend production build：`npm run build` | 通过；`vue-tsc --noEmit` 与 Vite build 均通过 |
| Generated contract tests | 通过；116 项请求契约测试 |
| Python：`python -m pytest -q --basetemp=.pytest-tmp` | 通过；20 项 Python 测试 |
| SOP manifest：`validate_sop_manifest.py --manifest docs/smart-canteen/sop-manifests.yaml --run sop-runs/menu-to-traceability.yaml` | 通过；支付 SOP 可追溯到 SC-011 证据 |
| Project-to-Act：`init_project_management.py --validate` | 通过；`valid=true`，`issues=[]` |
| Agent lifecycle：`manage_lifecycle.py ... validate` | 通过；revision 9，`projectStatus=completed`，未改写已完成生命周期 |
| Git whitespace：`git diff --check` | 通过 |

## Covered behavior

- `POST /api/v1/meal-orders/{orderId}/pay` 仅允许当前 actor 的 `CREATED + UNPAID` 订单。
- 支付记录固定为 `STUDY_MOCK + SUCCEEDED`，订单只从 `UNPAID` 变为 `PAID`，不改变订单 `CREATED` 状态。
- `actor + scope + Idempotency-Key` 重放返回同一业务结果；同键不同载荷和同订单不同 Key 均拒绝。
- 已支付订单不能取消；跨用户支付被拒绝；助手支付必须先确认，预览阶段不写库。
- 员工端模拟支付成功或失败后都会刷新个人订单列表；支付失败会保留错误提示。
- OpenAPI、API IR、生成客户端、员工端 API 与 `DinerWorkspace` 支付入口已同步。
