# Ledger SOP

## Purpose

按学校、食堂和周期维护台账要求，完成缺项聚合和预警清除。

## Steps

1. 创建或恢复 `schoolId + canteenId + cycleId` 周期和台账要求。
2. 校验台账编码属于当前周期，再写入内容和附件引用。
3. 重复完成同一台账返回已有结果，不产生额外副作用。
4. 聚合缺项；完成最后一项时返回 `CLEARED` 并清除当前预警。
5. 生成查询、统计和重启恢复证据。

## Evidence

- Requirements: `LEDGER-001`、`LEDGER-002`、`LEDGER-003`、`LEDGER-004`。
- Implementation: `LedgerMonitoring`、`ConfigurableLedgerService`、`ledger_*` 表。
- Verification: `LedgerAlertServiceTest`、`LedgerCycleHttpTest`、持久化重启测试。
