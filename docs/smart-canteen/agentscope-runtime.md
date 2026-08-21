# AgentScope Java 2.x Runtime（SC-003）

当前项目采用“现有业务 Runtime + AgentScope 可选 HarnessAgent”的渐进接入方式。

```text
HTTP Assistant
    ↓
AssistantConversationService
    ↓
AssistantIntentResolverRouter
    ├─ RuleBasedAssistantIntentResolver（默认优先）
    └─ AgentScopeAssistantModelResolver（可配置）
          ↓
       HarnessAgent
          ↓
       OpenAI-compatible Model / DeepSeek
```

## 角色上下文

`BusinessAuthorizationPolicy` 先根据认证主体建立 `ExecutionContext`，然后转换成不可变的
`AssistantRoleContext`：

| 服务端角色 | Assistant Persona | 当前助手边界 |
|---|---|---|
| `DINER` | `EMPLOYEE_STUDENT` | 菜单/菜品等只读能力 |
| `CANTEEN_STAFF` | `CANTEEN_OPERATOR` | 运营查询和后续运营分析 |
| `SCHOOL_ADMIN`、`SYSTEM_ADMIN` | `CANTEEN_MANAGER` | 管理分析和审批能力 |

角色、权限、学校和食堂范围均来自服务端；用户消息不能覆盖这些字段。AgentScope 的
`RuntimeContext` 只接收 `userId`、请求级 `sessionId` 和摘要后的角色上下文，业务 Tool 仍由
现有 `SkillRegistry`、`AgentRuntime`、`BusinessAuthorizationPolicy` 和业务服务负责。
该角色上下文只在显式启用 `agentscope` provider 时进入 HarnessAgent；默认的
`deepseek-http` 适配器保留原有的消息级边界，不把服务端身份摘要发送到外部模型。

## AgentScope 通道

后端默认保持原来的 `deepseek-http` 解析器。需要在本地学习环境启用 AgentScope 通道时：

```powershell
$env:ASSISTANT_MODEL_PROVIDER = "agentscope"
$env:ASSISTANT_MODEL_API_KEY = "<local-study-key>"
$env:ASSISTANT_MODEL_NAME = "deepseek-v4-flash"
$env:ASSISTANT_MODEL_BASE_URL = "https://api.deepseek.com"
mvn spring-boot:run
```

AgentScope 适配使用 `agentscope-harness` 和 OpenAI 兼容模型扩展，显式关闭文件系统、Shell、
Memory、SubAgent、动态 Skill 和 tools 配置；本阶段不会让 HarnessAgent 直接访问数据库或
执行采购、库存、支付等业务动作。

## 后续接入顺序

1. SC-004：把菜单、库存和采购查询作为现有业务 Tool 暴露给助手。
2. SC-005：增加确定性库存缺口和备餐分析服务，LLM 只解释结果。
3. SC-006：在有明确价值时再接 MCP、SSE 流式事件和 SubAgent。

当前不启用 AgentScope 的 Sandbox、SubAgent、MCP 和持久 Memory；它们属于后续需求驱动的
扩展点，不是首个可运行切片的前置条件。
