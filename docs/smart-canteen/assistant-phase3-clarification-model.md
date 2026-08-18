# 智能业务助手：第三阶段模型适配与澄清编排

> 本文档记录第三阶段交付时的历史快照。当前模型适配器见
> [assistant-model-deepseek.md](./assistant-model-deepseek.md)，默认关闭并仍需部署凭据、网络策略和 smoke test。

## 交付范围

本阶段在只读菜单/溯源助手上增加可恢复的会话澄清状态机，并建立模型解析适配器边界：

- 不完整的溯源或菜单请求会进入持久化 `WAITING_CLARIFICATION` 会话状态；
- 用户在同一会话补充 `TRACE-...` 或 `MENU-...` 标识后，系统合并上下文并继续原有只读 Runtime 链路；
- 无效补充会继续澄清，不会调用工具；成功结果或明确不支持请求会清除待澄清状态；
- `AssistantModelResolver` 是可替换端口，模型输出必须经过意图和资源 ID 白名单校验；默认 `SMART_CANTEEN_ASSISTANT_MODEL_ENABLED=false`，本阶段快照实现为 port-only，不伪装外部模型已接通。

## 使用方式

```text
用户：帮我查一下这批食材的溯源
助手：请提供批次溯源码，例如 TRACE-001。
用户：TRACE-ASSIST-001
助手：返回同一学校/食堂范围内的溯源结果，并关联 Agent Run。
```

菜单查询同样支持续问：先说“帮我看看今天的菜单”，再补充 `MENU-...`。

## 状态与安全边界

```text
ACTIVE（无待澄清）
  └─ 缺字段 → WAITING_CLARIFICATION（V18 持久化）
       ├─ 合法标识 → 清除待澄清并回到 ACTIVE → Runtime/Policy/Tool/Service
       ├─ 无效补充 → 继续 WAITING_CLARIFICATION，不调用工具
       └─ 新意图/不支持 → 清除旧状态并回到 ACTIVE，按新消息重新处理
```

模型路由只处理规则解析器无法识别的消息；采购、库存、预警、台账等已明确识别为不支持的请求不会交给模型改写。即使开启模型开关，模型也不能选择未注册 Skill、绕过 `BusinessAuthorizationPolicy` 或提交任意资源 ID。真实模型供应商接入仍需合同、凭据、网络策略和独立安全验收。

## 当前限制

- 本阶段快照中的 port-only 模型适配器始终返回空结果；当前 DeepSeek 适配器仍默认关闭，生产行为只有在显式部署配置并通过灰度门禁后才会调用模型；
- 澄清仅覆盖溯源 ID 和菜单 ID，不覆盖日期、餐次、采购、库存或写操作；
- 仍没有“预览—确认—执行”的业务写闭环；该闭环必须在后续菜单/采购领域门禁完成后开放。
