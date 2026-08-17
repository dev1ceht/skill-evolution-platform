# SOP contract

## Manifest fields

每个 SOP 条目至少声明：

- `id`、`version`、`trigger`：稳定标识、版本和触发意图；
- `scope`、`permissions`、`risk_level`、`approval`：作用域、角色、风险分类和审批门槛；
- `preconditions`、`steps`、`idempotency`、`rollback`：前置条件、顺序步骤、重复请求语义和失败恢复；
- `requirements`、`implementation`、`adapters`：业务依据、代码/接口/迁移/测试位置和外部边界；
- `evidence`、`status`：验证证据和 `implemented`、`port-only`、`deferred` 或 `environment-gated` 状态。

`timeout` 可以声明为 `request-bound` 或 `not-configured`。未有运行时配置时必须如实标记，不能把设计字段当成已经执行的超时控制。

## Run record

运行记录至少包含：

```yaml
schema_version: "1.0"
run_id: "run-..."
skill: "smart-canteen-sop"
skill_version: "1.0.0"
sop_id: "smart-canteen.menu-approval"
scope: {school_id: "SCHOOL-001", canteen_id: "CANTEEN-001"}
actor_role: "CANTEEN_STAFF"
status: "passed"
steps:
  - id: "draft"
    status: "passed"
evidence:
  requirements: ["MENU-001"]
  tests: ["backend/src/test/java/.../OperationalCoreHttpTest.java"]
  verification: ["docs/smart-canteen/phase5-verification.json"]
```

运行记录描述已经发生的行为；SOP Manifest 描述允许发生的行为，两者不能互相替代。
