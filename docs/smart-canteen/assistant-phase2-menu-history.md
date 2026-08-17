# 智能业务助手：第二阶段菜单查询与会话历史

## 交付范围

本阶段在第一阶段只读溯源切片上增加两个能力：

- 自然语言识别 `menu.query`，通过 Skill Registry、Agent Runtime、`menu.query` 工具和 `DailyMenuService` 查询日菜单；
- 读取当前用户、当前学校/食堂范围内的会话历史，并在前端助手工作区恢复已保存的用户消息与助手结果。

菜单查询仍然是只读操作，不会提交、审批或发布菜单。`MENU_READ` 权限由 `V17__add_menu_read_permission.sql` 注册，并由 Agent 执行前再次校验。

## 使用方式

在助手工作区输入：

```text
请查询 MENU-001 的午餐菜单
```

历史接口：

```http
GET /api/v1/assistant/conversations/{conversationId}/messages
    ?schoolId=SCHOOL-001&canteenId=CANTEEN-001&limit=50
```

前端会按学校/食堂把会话 ID 保存在浏览器本地存储；重新打开页面后会读取该会话的历史。后端对会话所有者和范围做二次校验，未创建过的会话返回空历史，不会因为读取操作创建数据库记录。

## 当前边界

- 解析器仍是可审计规则解析器，只识别带 `MENU-...`/`MENU_...` 标识的菜单查询；
- 菜单查询通过 Agent Run 留存运行状态和结果快照；
- 仍不支持自然语言采购、库存、预警或任何写操作；
- 历史接口当前按轮次限制返回，分页游标和长期留存策略留待后续阶段。

## 验证证据

详见 [`assistant-phase2-verification.json`](assistant-phase2-verification.json)。
