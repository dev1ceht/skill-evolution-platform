# API compatibility matrix

| Change | Default classification | Required action |
| --- | --- | --- |
| Add operation | compatible | add authorization and tests |
| Add optional request field | compatible | define omission behavior |
| Add required request field | breaking | version or compatibility default |
| Add response field | compatible with review | verify consumers ignore unknown fields |
| Remove/rename response field | breaking | dual field or new version |
| Change method/path/operation identity | breaking | keep adapter/old route during migration |
| Widen accepted enum | review-required | verify downstream handling |
| Narrow enum or change stored meaning | breaking | data and consumer migration |
| Change error code/status | review-required or breaking | update error adapters and recovery UX |
| Change pagination/sort/default scope | review-required or breaking | explicit migration and regression cases |
| Change auth/idempotency requirements | breaking | coordinated rollout and security review |

## Deprecation record

Record the owner, deprecated operation/version, replacement, known consumers, announcement date, target removal date, usage signal, compatibility tests and exception approver. A date alone is not proof that removal is safe.

