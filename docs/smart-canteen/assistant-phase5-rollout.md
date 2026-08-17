# 助手试点范围灰度门禁

本阶段把助手已有的全局 kill switch 收紧为“全局开关 + 食堂范围白名单”。它只控制自然语言助手 HTTP 入口，不改变现有菜单、采购、库存和预警页面路径。

## 配置

生产环境必须显式配置：

```text
SMART_CANTEEN_ASSISTANT_ENABLED=true
SMART_CANTEEN_ASSISTANT_SCOPES=SCHOOL-PILOT/CANTEEN-PILOT
```

`SMART_CANTEEN_ASSISTANT_SCOPES` 是逗号分隔的 `SCHOOL-ID/CANTEEN-ID` 列表。全局开关关闭，或请求范围不在列表中时，助手入口返回 `403`。开关开启但列表为空或包含非法值也会拒绝启动/拒绝全部范围，确保配置缺失时 fail closed。

关闭助手后，既有业务页面仍通过原有授权策略和领域状态机工作。

## 边界

- `POST /api/v1/assistant/conversations/{conversationId}/messages` 和会话历史 `GET` 都在创建执行上下文、读取或写入会话前检查灰度范围；
- 范围白名单不替代 `BusinessAuthorizationPolicy`，用户身份、角色、权限和 `schoolId + canteenId` 仍由服务端重新校验；
- 该门禁只开放已经验收的助手能力：只读溯源、菜单查询，以及受确认保护的菜单发布；不会开启采购、库存或预警写入；
- 运行指标看板、真实 MySQL 并发验收和生产凭据/网络门禁仍是后续上线条件。

## 回退

将 `SMART_CANTEEN_ASSISTANT_ENABLED=false` 重新部署即可关闭助手入口；不需要数据库迁移，也不会删除已保存的会话或 Agent Run。关闭期间应由原页面路径或人工流程接管待办业务。
