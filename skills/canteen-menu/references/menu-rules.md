# Canteen menu rules

## Input

```yaml
scope: {school_id: "SCHOOL-001", canteen_id: "CANTEEN-001"}
menu:
  id: "MENU-2026-08-20-LUNCH"
  date: "2026-08-20"
  meal: "lunch"
  version: 1
  status: "draft"
  dishes:
    - {dish_id: "DISH-001", name: "番茄炒蛋", recipe_id: "RECIPE-001", servings: 100}
```

`status` 只能是 `draft`、`submitted`、`approved` 或 `published`。每个餐次只能有一个有效发布版本；菜品必须有配方引用和正数份数。

## State transitions

```text
draft --submit--> submitted --approve--> approved --publish--> published
submitted/approved --reject(reason)--> draft
```

发布状态不可直接编辑。任何菜单变更都要增加版本并重新提交审批。脚本只生成业务文件，不绕过后端授权或数据库事务。
