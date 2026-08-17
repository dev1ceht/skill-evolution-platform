# 智能业务助手：第四阶段菜单发布预览与确认

## 交付范围

本阶段把自然语言入口从只读查询推进到一个受约束的菜单发布写入切片：

- “请发布 `MENU-001`”只生成 `menu.publish` Agent Run 计划，不执行业务写入；
- 计划以不可变的菜单 ID、菜单版本、范围和 `planHash` 持久化到会话待处理动作；
- 只有同一会话中明确回复“确认发布/执行”等确认词，才调用 `RUN_CONFIRM`，再由现有 `AgentExecutionService` 执行；
- “取消/取消发布”等明确取消词会调用 `RUN_CANCEL`，清除待处理动作，不产生菜单副作用；
- 发布瞬间仍由 `BusinessAuthorizationPolicy`、`DailyMenuService` 和 `MenuToolExecutor` 重新检查权限、范围、食堂状态、菜单版本、审批状态、职责分离和业务幂等。

该切片不新增 HTTP 操作，只对现有 `AssistantTurn.kind` 契约做兼容性扩展，补充 `CONFIRMATION_REQUIRED`。它不把 Agent 运行确认当成菜单领域审批。菜单必须已经是 `APPROVED`，并且发布操作者不能同时是提交人或审批人；版本发生变化时，旧计划会被拒绝并要求重新计划。

## 状态机

```text
ACTIVE
  └─ “发布 MENU-*”
       └─ Agent Run WAITING_CONFIRMATION
          + assistant_pending_actions 持久化计划
          ├─ “确认发布/执行”
          │    └─ RUN_CONFIRM → PLANNED → AgentExecutionService → SUCCEEDED/失败分类
          │                         └─ 清除待处理动作，conversation → ACTIVE
          └─ “取消/取消发布”
               └─ RUN_CANCEL → CANCELLED
                                  └─ 清除待处理动作，conversation → ACTIVE
```

待确认期间收到查询、未支持事项、不完整请求或另一条发布请求时，助手不会执行新工具，也不会删除旧计划；它会重复展示确认/取消提示，并保持 `WAITING_CONFIRMATION`。如需改发另一份菜单，必须先明确“取消”，再发起新的“发布 MENU-*”请求。

如果同一个 Agent Run 已经通过 Agent API 被其他入口确认、取消或进入其他终态，下一条助手消息会按 Run 的版本、计划哈希和状态做对账：清除失效的会话待处理动作，并返回当前 Run 状态，不会再次执行或把会话永久卡在待确认。采购、库存、预警等明确未支持词会在发布匹配前被拒绝，不能通过补充菜单 ID 误开 `menu.publish` 澄清。

`WAITING_CLARIFICATION` 仍用于缺少菜单 ID 的请求；澄清、确认和取消都在会话锁内与消息追加一起提交。助手消息的 `kind=CONFIRMATION_REQUIRED` 携带 Run 计划，前端只展示摘要，不允许编辑计划参数。

## 使用方式

```text
用户：请发布 MENU-001
助手：已生成菜单发布计划……回复“确认发布”执行，回复“取消”放弃。
用户：确认发布
助手：已完成菜单发布：MENU-001。
```

如果菜单还未审批、版本已经变化、操作者越权或食堂停用，确认请求不会绕过领域门禁；系统返回失败/阻塞状态，保留现有页面和人工处理路径。

## 安全与上线边界

- 规则解析器只识别固定的发布、确认和取消表达式；默认 port-only 的模型适配器不能产生写意图。
- 当前只开放 `menu.publish`，采购、库存、预警、台账等写操作仍未接入自然语言入口。
- 真实外部模型、外部供应商和生产灰度仍需合同、凭据、网络策略、指标和人工接管验收。
- `SMART_CANTEEN_ASSISTANT_ENABLED=false` 或既有 kill switch 关闭时，旧业务页面路径不受影响。
