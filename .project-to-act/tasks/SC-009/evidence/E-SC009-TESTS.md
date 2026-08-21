# E-SC009-TESTS

## 范围

本轮完成员工/学生端菜单与个人消费订单垂直切片：读取已公示菜单、创建未支付订单、查询本人订单、取消本人 CREATED 订单。支付、评价/投诉、POS、库存扣减和真实预测服务不在范围内。

## 自动化验证

| 命令 | 结果 |
|---|---|
| `mvn -q -DskipTests compile` | 通过 |
| `mvn -q -Dtest=YamlSkillRegistryTest,RuleBasedAssistantIntentResolverTest,MealOrderToolExecutorTest,MealOrderControllerHttpTest test` | 通过：50 tests，0 failures，0 errors |
| `mvn -q test` | 通过：281 tests，0 failures，0 errors，2 skipped |
| `npm run build`（`frontend`） | 通过 |
| `npm run test`（`frontend`） | 通过：138 tests，0 failures（含 111 个生成客户端契约测试） |
| `python -m pytest -q --basetemp .pytest-tmp-sc009` | 通过：20 tests，0 failures |
| `python skills/smart-canteen-sop/scripts/validate_sop_manifest.py --manifest docs/smart-canteen/sop-manifests.yaml` | 通过：14 SOPs、1 composition |
| `python C:\Users\th\.agents\skills\project-to-act\scripts\init_project_management.py --project-root D:\project\smart-canteen --validate` | 待最终收口时复核 |
| `python C:\Users\th\.agents\skills\develop-ai-agents\scripts\manage_lifecycle.py --project-root D:\project\smart-canteen validate` | 待最终收口时复核 |
| `git diff --check` | 待最终收口时复核 |

## HTTP 行为证据

- `GET /api/v1/diner/menus` 只返回当前范围的已公示菜单和菜品快照。
- `POST /api/v1/meal-orders` 从服务端登录身份取得 `actorUserId`，创建 `CREATED + UNPAID` 订单；请求体不能覆盖操作者。
- 相同 `Idempotency-Key` 和相同请求体返回同一订单；同键不同菜品数量被拒绝。
- 相同 `Idempotency-Key` 在不同登录用户之间互不冲突；同一用户范围内仍按载荷哈希拒绝错误重放。
- `GET /api/v1/meal-orders` 只返回当前用户自己的订单；其他用户不能看到或取消该订单。
- 订单本人可以取消 `CREATED` 订单；重复取消已取消订单保持幂等。
- Agent `meal_order.query` 为只读 Skill；`meal_order.create` 与 `meal_order.cancel` 进入写入确认链路，并使用运行步骤注入的业务幂等键。
- OpenAPI、`contracts/generated` 和 `frontend/src/api/generated` 已同步包含菜单/订单接口与类型。

## 当前边界

- `unitPrice`、`amount`、`totalAmount` 当前固定为 0，仅表示学习研究环境的未支付订单，不代表真实收款事实。
- 数据库、领域模型和 OpenAPI 共同约束 `paymentStatus` 只能为 `UNPAID`。
- 未接支付、评价/投诉、POS、库存扣减和 MCP；后续扩展需另建业务闭环和测试证据。
