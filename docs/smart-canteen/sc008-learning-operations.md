# SC-008 学习环境运行记录

SC-008 是个人学习环境中的“采购申请 Draft 生成”切片。它复用已有采购计划领域服务，
不接入真实预测服务，也不把大模型生成的数量当作业务事实。

## 业务边界

- `procurement.plan.generate` 是唯一使用的写意图；自然语言“采购申请草稿”“采购 Draft”只补充该意图的入口表达。
- 计划周期来自用户明确提供的 ISO 日期或“今天/明天”；缺少日期时先澄清，不创建 Agent Run。
- `ProcurementPlanService.generate` 只读取当前食堂范围内的已发布菜单、菜单估算份数、Recipe/BOM、库存和在途采购快照。
- 首次助手消息只创建 `WAITING_CONFIRMATION` 运行计划；回复“确认”后才创建状态为 `DRAFT` 的采购计划。
- 本切片不确认采购计划、不创建采购订单、不提交采购、不收货入库、不改库存。
- `CANTEEN_STAFF`、`SCHOOL_ADMIN`、`SYSTEM_ADMIN` 可使用既有 `PROCUREMENT_PLAN_WRITE` 权限；`DINER` 不可使用。

## 端到端样例

```text
帮我生成明天的采购申请草稿
        ↓
WAITING_CONFIRMATION（periodStart=periodEnd=明天）
        ↓ 回复“确认”
procurement.plan.generate
        ↓
ProcurementPlanStatus.DRAFT
```

计划项的 `requiredBaseQuantity`、`inventoryBaseQuantity`、`openOrderBaseQuantity`、
`shortageBaseQuantity` 和 `plannedBaseQuantity` 均来自确定性业务服务；助手只负责解释结果。

## 验证

```powershell
cd backend
mvn -q '-Dtest=RuleBasedAssistantIntentResolverTest,AssistantControllerHttpTest' test
mvn -q test
cd ..
python -m pytest -q --basetemp .pytest-tmp-sc008
python skills/smart-canteen-sop/scripts/validate_sop_manifest.py `
  --manifest docs/smart-canteen/sop-manifests.yaml `
  --run sop-runs/menu-to-traceability.yaml
```

HTTP 验收覆盖预览无落库、明确确认后生成 `DRAFT`、Recipe/BOM 缺口计算、助手消息幂等重放和采购订单数为零。
