# SC-010 员工评价与投诉证据

日期：2026-08-21

## 范围

- 员工/学生对本人未取消消费订单提交一次 1～5 星评价，并查询个人评价。
- 员工/学生提交分类、主题、描述和可选关联订单的投诉，并查询个人投诉。
- 助手评价/投诉写入先生成 `WAITING_CONFIRMATION`，确认后才执行。
- 支付、退款、评价分析、投诉管理端受理/回复/关闭、POS 和库存扣减不在本切片。

## 验证记录

以下记录在本切片完成收口后更新，命令和结果必须与工作区实际执行一致。

| 检查 | 结果 | 说明 |
|---|---|---|
| Maven 员工评价/投诉定向测试 | 通过（57 tests，0 failures，0 errors） | `MealReviewToolExecutorTest` 4、`DinerComplaintToolExecutorTest` 3、`EmployeeFeedbackControllerHttpTest` 3、`RuleBasedAssistantIntentResolverTest` 45、`YamlSkillRegistryTest` 2 |
| Maven 全量测试 | 通过（296 tests，0 failures，0 errors，2 skipped） | SC-009 既有菜单/订单、采购、Agent Runtime 和本切片回归已执行通过 |
| 前端构建 | 通过 | `vue-tsc --noEmit && vite build` |
| 前端测试 | 通过（145 tests） | 员工端评价/投诉、API 映射和既有工作台测试 |
| Python/SOP 校验 | 通过（20 tests） | SOP manifest 验证通过：18 个 SOP、1 个组合运行 |
| Project-to-Act 校验 | 通过 | `valid=true`、`schema_version=1`、`issues=[]` |

## 业务边界证据

- 数据表：`meal_reviews`、`diner_complaints`。
- 服务端身份从 `AuthPrincipal` 取得，订单归属、订单状态、评价唯一性、投诉状态和幂等键由服务端校验。
- Agent Tool 只接收结构化参数，不直接访问数据库；`meal_review.create` 和 `diner_complaint.create` 通过现有 Agent Run 确认链路执行。
- 个人查询按 `schoolId + canteenId + actorUserId` 限定，不返回其他用户记录。
