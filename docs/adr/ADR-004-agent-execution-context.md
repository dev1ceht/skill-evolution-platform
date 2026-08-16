# ADR-004：Agent 使用服务端派生的执行上下文

## 状态

Accepted for Agent Runtime Phase 0.

## 背景

当前 HTTP Controller 通过 `AuthenticationInterceptor`、`ScopeAccess` 和 `RoleAccess` 校验登录身份、角色、权限及 `schoolId + canteenId` 范围。Agent Runtime 还需要在没有 `HttpServletRequest` 的同步工具调用、审批恢复和进程重启恢复场景中执行同样的规则。

如果 Agent 从请求体读取用户 ID、角色或食堂范围，或者伪造一个 HTTP 请求来复用 Controller 组件，就会产生越权和异步恢复时的身份漂移。

## 决策

- 由认证边界解析 `AuthPrincipal`；请求体中的操作者、角色和权限声明不具有可信性。
- `BusinessAuthorizationPolicy` 负责请求无关的范围、角色、权限和食堂启停校验，HTTP Controller 与 Agent 共同调用它。
- `ExecutionContext` 只由已认证的服务端主体、当前请求 ID、经校验的业务范围以及当前持久化角色/权限构造。
- Run 持久化操作者 ID、范围和策略快照；恢复或审批时重新加载当前授权，不把历史角色当作永久授权。
- 业务写入必须显式绑定 `schoolId + canteenId`，不得回退到 `CanteenScope.DEFAULT`。

## 后果

- 现有 `ScopeAccess` 和 `RoleAccess` 变成 HTTP 适配层，业务授权规则不再绑定 Servlet API。
- Agent 可以直接调用应用服务，但仍然必须先通过同一授权策略；不需要 HTTP 自调用。
- 安全关闭配置仍可用于本地开发，但 Runtime 上下文本身仍要求服务端身份和显式范围，测试必须使用测试主体而不是匿名请求体。
- 异步恢复需要重新解析操作者权限；如果主体已禁用、范围已撤销或食堂已停用，Run 必须阻塞或拒绝。
