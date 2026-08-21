# SC-007 学习环境运行记录

SC-007 是个人学习环境中的“客流预测与备餐建议只读分析”切片。预测数值来自仓库内可复现的
study fact，当前不连接真实 POS、门禁、预约、天气或节假日服务。

## 业务边界

- `traffic.forecast.query` 只读取 `traffic_forecasts` 的版本化事实。
- `meal_plan.query` 只读取预测事实和已发布菜单，并用
  `PROPORTIONAL_MENU_ESTIMATE_LARGEST_REMAINDER` 分配推荐份数。
- 缺少预测事实或已发布菜单时返回 `available=false` 和原因，不猜测、不补默认数量。
- 本切片没有备餐计划、采购计划、采购订单、库存调整或菜单发布 Tool。
- `CANTEEN_STAFF`、`SCHOOL_ADMIN`、`SYSTEM_ADMIN` 可使用分析权限；`DINER` 没有这两个权限。

## 本地学习数据

`data/study/smart-canteen-study-dataset.yaml` 声明一条 2026-08-22 一食堂午餐预测事实：

- 预计用餐人数：850
- 区间：810～880
- 模型版本：`study-traffic-v1`
- 来源：`GENERATED_STUDY_FACT`

对应 SQL 位于 `data/study/smart-canteen-study-seed.sql`，先执行 Flyway V31/V32/V33 后再导入。

## 观察与验证

建议在本地执行：

```powershell
cd backend
mvn -q -Dtest=AssistantControllerHttpTest test
mvn -q test
cd ..
python -m pytest -q --basetemp .pytest-tmp-sc007
python skills/smart-canteen-sop/scripts/validate_sop_manifest.py `
  --manifest docs/smart-canteen/sop-manifests.yaml `
  --run sop-runs/menu-to-traceability.yaml
```

端到端断言包括：自然语言路由到两个只读 intent、结果使用版本化事实、最大余数分配总量等于 850，
以及备餐/采购计划表和菜单状态不发生写入。

## 退役与回滚

回退 SC-007 变更即可移除 `traffic.forecast.query`、`meal_plan.query`、V31/V33 预测事实表版本链路和 V32
分析权限；SC-006 的菜单原料缺口分析与既有采购写入链路不在本切片回滚范围内。
