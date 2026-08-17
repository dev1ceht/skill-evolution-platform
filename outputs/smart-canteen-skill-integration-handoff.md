# 智慧食堂业务 Agent 与 Skill 交接

仓库当前以智慧食堂为主项目，已形成可运行、可测试、可审计的业务纵向切片。用户操作可以通过页面或 Agent 进入 `smart-canteen-sop`，由 Manifest 约束权限、审批、状态、幂等、超时、回滚和证据。

## 当前闭环

```text
菜单审批 → 采购计划/订单 → 验收入库 → 库存与台账 → 预警处置 → 食品溯源
```

## 关键入口

- 业务总览：[`README.md`](../README.md)
- 领域语言：[`CONTEXT.md`](../CONTEXT.md)
- OpenAPI：[`contracts/smart-canteen.openapi.yaml`](../contracts/smart-canteen.openapi.yaml)
- Spring Boot：[`backend/`](../backend/)
- Vue：[`frontend/`](../frontend/)
- 业务 SOP Skill：[`skills/smart-canteen-sop/SKILL.md`](../skills/smart-canteen-sop/SKILL.md)
- 运行记录：[`sop-runs/menu-to-traceability.yaml`](../sop-runs/menu-to-traceability.yaml)

## 尚未接通的外部能力

真实微信/SSO、区县平台、明厨亮灶、晨检设备、对象存储和通知渠道仍需要厂商合同、凭据和网络策略。当前只提供端口、规范化模型或契约测试，不能把这些边界描述为已完成生产接入。
