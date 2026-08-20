---
name: canteen-menu
description: 校验食堂日食谱、菜品和配方，并执行草稿、提交、审批、发布等菜单状态流转。Use when用户要创建或检查菜单、提交食谱审批、发布餐次菜单、退回修改或生成采购前的菜单快照时。
---

# Canteen Menu

这个 Skill 直接处理菜单运营，不负责前端页面开发。它读取菜单文件，校验学校/食堂范围、餐次、菜品和配方状态，再按业务允许的状态迁移生成新版本。

## Workflow

1. 读取 [menu-rules.md](references/menu-rules.md)，确认输入结构和当前菜单状态。
2. 运行 `scripts/transition_menu.py`，传入目标状态和操作者角色。
3. 对 `submitted`、`approved` 和 `published` 等有副作用的状态，在写出结果前展示变更并等待用户确认。
4. 保存状态历史、操作者、原因和输出文件；发布后不能静默覆盖。

```powershell
python scripts/transition_menu.py menu.yaml `
  --to published --actor-role MENU_MANAGER --output published-menu.yaml
```

## Business rules

- 只处理一个 `school_id + canteen_id` 范围。
- 草稿必须包含有效日期、餐次、菜品和配方引用。
- 合法主流程是 `draft → submitted → approved → published`。
- 发布后的修改必须产生新版本或重新走审批，不能原地覆盖。
- 被退回的菜单必须记录原因；没有原因不能回到草稿。

## Output

输出菜单状态、版本、状态历史、阻断原因和可供采购 Skill 消费的已发布菜单快照。
