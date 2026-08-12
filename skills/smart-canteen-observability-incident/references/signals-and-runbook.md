# Signals and runbook

## Minimum signals

| Journey/dependency | Useful signals |
| --- | --- |
| Menu approval | submissions, decisions, invalid transitions, latency |
| Procurement planning | plan success/failure, missing recipe, negative-gap prevention |
| Inventory receiving | receive success, idempotent replay, unit-conversion rejection, transaction rollback |
| Ledger cycle | open items, completion conflicts, time to `CLEARED` |
| Alert center | ingest rate, duplicate/conflicting source events, disposal conflicts, query latency |
| External adapters | request rate, timeout, retry, circuit state, vendor error class |
| MySQL | pool saturation, query latency, deadlocks, migration version, storage |
| Redis | hit/miss only after real caching exists, latency, eviction, connection failures |
| RabbitMQ | publish/consume failures, unacked count, retry/dead-letter rate and oldest-message age |
| Skill evolution | pending age, candidate decisions, replay failures, promotion/rollback and recovery-required intents |

Avoid unbounded metric labels such as raw user IDs, menu IDs, warning IDs, URLs or error messages. Logs may carry a controlled correlation ID; metrics use bounded categories.

## Incident timeline template

```text
time | observation/action | evidence source | result | next hypothesis
```

## Recovery proof

Verify one representative business flow, persisted state after restart where relevant, queue/cache convergence, error/latency return to baseline and absence of new cross-tenant or duplicate effects.

