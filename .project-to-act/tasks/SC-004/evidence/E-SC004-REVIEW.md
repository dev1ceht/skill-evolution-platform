# E-SC004-REVIEW

## 评审基线

- 固定点：`dad085cc29358841f2ecd709716d00f3bb6beac2`
- 范围：该固定点到当前 SC-004 工作区变更，包含新增 Task Contract、Tool 测试和 OpenAPI 调整。
- 评审轴：Spec 与 Standards 并行评审。

## 首轮发现与处理

1. 日期解析正则最初只接受 `20xx`；已改为完整四位年份并增加 1999 日期测试。
2. `MenuQueryIntent` 最初仍只声明 `menuId`；已改为 `menuId` 或 `menuDate + mealTime` 的互斥 `oneOf`。
3. SOP 引用的 `MenuQueryResult` 最初未定义；已在 OpenAPI 中补充单菜单/分页结果联合结构。
4. 本切片原先额外支持“明天”菜单查询；已移除，保持范围为今天和指定日期。
5. Tool 与模型适配器最初会静默优先 `menuId`；已统一拒绝 `menuId` 与日期/餐次的歧义组合，并补充 Tool 测试。

## 当前结论

- Spec：通过；无剩余功能性阻塞。
- Standards：无仓库硬性规范违规。评审指出日期/餐次在规则解析器、模型适配器、领域解析和 Tool 层存在重复校验，属于可接受的当前切片设计气味，后续可抽取共享值对象；不影响本次验收。
- 已知延期：真实 AgentScope Provider 仍因未配置模型密钥而不做在线烟囱测试，继承 SC-003 的个人学习环境延期项。
