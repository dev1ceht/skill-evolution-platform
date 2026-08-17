# Alert and disposal SOP

## Purpose

接收归一化外部预警，保存处置状态并支持可审计的重复提交。

## Steps

1. 在 Adapter 边界校验来源、`thirdWarnId`、学校/食堂范围、发生时间和事件内容。
2. 以 `source + thirdWarnId` 去重；同载荷重试返回原记录，同键异载荷拒绝并审计。
3. 按角色和范围查询预警记录。
4. 提交处置结果；相同结果幂等，不同结果不能覆盖已处置记录。
5. 对临期合规记录复用统一 AlertCenter，不在外部通知渠道中复制领域状态。

## Evidence

- Requirements: `ALERT-001`、`ALERT-002`、`DATA-004`、`ARCH-005`。
- Implementation: `AlertCenterService`、`AlertCenterController`、`AlertSourceAdapter`、`alert_records`。
- Verification: `AlertCenterModuleTest`、`AlertCenterHttpTest`、Flyway persistence tests。
