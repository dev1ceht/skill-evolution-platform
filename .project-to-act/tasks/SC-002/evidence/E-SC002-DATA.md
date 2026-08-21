# E-SC002-DATA

- 时间：2026-08-21
- 范围：`data/study/smart-canteen-study-dataset.yaml` 与 `data/study/smart-canteen-study-seed.sql`
- 环境：本地 MySQL 8.4、项目工作区 `D:\project\smart-canteen`
- 验证方法：YAML 结构检查；seed SQL 导入；同一 SQL 重复导入；缺口、溯源和预警查询。
- 退出状态：0
- 结果摘要：9 个食材、5 个菜品、5 个菜单、3 个供应商、2 个采购单、9 个批次、1 个备餐计划、2 个预警和 1 条合规记录存在；重复导入成功；缺口查询返回鸡肉、西兰花、鸡蛋、番茄和牛肉等学习数据；溯源查询返回 `TRACE-CHICKEN-20260820`。
- 证据位置：`data/study/`、`scripts/load-study-dataset.ps1`、`README.md`。
- 限制：数据为个人学习环境初始化事实，不代表真实食堂运营数据，不作生产结论。
