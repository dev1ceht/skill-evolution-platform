# 智能业务助手：第一阶段只读溯源切片

## 交付状态

第一条纵向切片已完成：用户可以在前端助手工作区输入自然语言，查询某个批次的食品溯源信息；请求会复用既有 Agent Runtime、技能注册表、权限策略和溯源工具，并把会话与每轮结果持久化。

当前解析器是可审计的规则解析器，不依赖外部大模型。它支持中文或英文的“溯源/追溯/traceability/trace”表达，并从文本中识别类似 `TRACE-001` 的溯源码。

## 使用方式

前端启动后，在“智能业务助手（只读试点）”区域输入：

```text
请查询 TRACE-001 的食品溯源信息
```

后端接口：

```http
POST /api/v1/assistant/conversations/{conversationId}/messages
Idempotency-Key: <唯一请求键>
Content-Type: application/json

?schoolId=SCHOOL-001&canteenId=CANTEEN-001
{"message":"请查询 TRACE-001 的食品溯源信息"}
```

响应包含自然语言答复、结构化 `result`、`runId`、运行状态和缺失字段。相同幂等键重放会返回同一轮结果；同一幂等键提交不同消息会被拒绝。

本地开发环境默认显示助手；生产环境需显式设置 `ASSISTANT_ENABLED=true` 和构建变量 `VITE_ASSISTANT_ENABLED=true` 才开放试点，任一开关关闭都会阻断/隐藏入口。

## 关键边界

- 只读：目前只开放 `traceability.query`，不会创建采购单、菜单或审批动作。
- 有权限边界：请求必须通过现有学校/食堂范围与技能权限校验。
- 入口可控：后端 `ASSISTANT_ENABLED` 与前端 `VITE_ASSISTANT_ENABLED` 均可关闭试点。
- 可追踪：成功查询关联 Agent Run；澄清和不支持请求也作为会话轮次保存。
- 安全输出：工具失败详情保留在受控 Run 状态中，用户消息只返回可操作的通用提示。
- 可恢复：会话和轮次由 `V16__create_assistant_conversations.sql` 建表，响应以 JSON 快照保存。
- 当前前端在工作区内维护消息列表；历史会话读取接口将在后续阶段补充。

## 验证证据

- 后端助手切片测试：10 项通过。
- 后端全量回归：113 项通过，1 项 MySQL 集成测试因环境未启用而跳过。
- 前端 Vitest：144 项通过。
- 前端生产构建：`vue-tsc --noEmit && vite build` 通过。
- Python/契约测试：14 项通过。

## 下一阶段入口

下一阶段应在保持“解析—授权—计划—执行—追踪”边界的前提下，增加菜单查询等只读意图，并提供会话历史读取；随后再引入模型解析适配器、澄清状态机和“预览—确认—执行”的写操作闭环。
