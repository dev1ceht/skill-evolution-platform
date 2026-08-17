# Menu approval SOP

## Purpose

管理指定学校食堂、日期和餐次的日食谱草稿、审批和发布。

## Steps

1. 校验操作者可访问 `schoolId + canteenId`，并确认菜品与配方处于可用状态。
2. 创建或覆盖草稿；同一范围、日期和餐次最多保留一份有效日食谱。
3. 提交审批并记录状态变化；拒绝时返回可修订原因。
4. 发布通过的食谱；发布后禁止静默修改，修改必须重新进入版本或审批流程。

## Evidence

- Requirement: `MENU-001`。
- Implementation: `DailyMenuService`、`daily_menus`、`daily_menu_items`。
- Verification: `OperationalCoreHttpTest`，覆盖发布和发布后修改拒绝。
