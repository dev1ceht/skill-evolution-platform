# Smart-canteen trust boundaries

| Boundary | Assets | Primary risks | Required controls |
| --- | --- | --- | --- |
| Browser → `/api/v1` | school/canteen business data | broken access control, injection, replay | server-side scope authorization, validation, idempotency, generic errors |
| Vendor/device → adapter | alerts, inspection and video metadata | spoofing, tampering, schema drift, replay | authenticated source, signature/TLS when supported, allowlist, normalization, source event uniqueness |
| Application → MySQL | durable domain and audit state | cross-tenant reads, partial writes, migration loss | composite scope keys, parameterized SQL, transactions, additive Flyway migrations, restore test |
| Application → Redis | cached or ephemeral state | stale data, key collision, secret exposure | scoped keys, TTL/invalidations, fallback behavior, authenticated local/private endpoint |
| Application → RabbitMQ | commands/events | duplicates, reordering, poison messages | message ID, idempotent consumer, bounded retry, dead letter, observable lag |
| User/Agent → Business SOP | operation parameters and business behavior | prompt injection, unauthorized write, scope loss | treat output as data, schema validation, scope authorization, approval, idempotency, audit |
| File/contract import → repository | OpenAPI, recipes and evidence | traversal, oversized input, malicious content | fixed root, size/type limits, schema validation, no command execution |

## Review questions

- 能否通过修改 `schoolId` 或 `canteenId` 读取/改写其他食堂数据？
- 同一请求或外部事件重复、乱序、延迟到达时结果是否确定？
- adapter 失败会不会留下半完成数据库状态？
- 日志和验证产物是否可能泄漏凭据、PII 或完整外部载荷？
- 外部系统不可用时，业务是失败关闭、延迟处理还是安全降级？
- 新权限、CORS、网络出口、数据类别或凭据是否需要人工批准？
