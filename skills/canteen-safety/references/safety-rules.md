# Canteen safety rules

## Event input

```yaml
scope: {school_id: "SCHOOL-001", canteen_id: "CANTEEN-001"}
source: "MORNING_INSPECTION"
event_id: "MORNING-20260820-001"
occurred_at: "2026-08-20T07:30:00+08:00"
severity: "HIGH"
description: "留样柜温度异常"
evidence: ["photo-001.jpg"]
```

`source + event_id` 是去重键；`severity` 为 `LOW`、`MEDIUM` 或 `HIGH`。`HIGH` 和 `MEDIUM` 事件进入 `needs_disposal`，只有带有责任人、处置说明和证据的处置记录才可以关闭。

## External boundary

设备和第三方平台响应必须先经过来源认证、字段校验和范围绑定。没有接口合同、凭据或网络策略时，受理结果只能说明 `adapter_status: port-only`。
