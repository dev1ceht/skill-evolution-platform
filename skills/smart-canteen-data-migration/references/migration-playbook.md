# Migration playbook

| Change | Preferred approach | Required evidence |
| --- | --- | --- |
| Add nullable column/table | expand in one migration | empty and upgrade migration tests |
| Add required field | nullable/defaulted expand, backfill, validate, later `NOT NULL` | null/conflict counts and new/old app compatibility |
| Add tenant scope | derive auditable mapping, backfill composite keys, add scoped indexes, then constrain | cross-tenant isolation and duplicate detection |
| Rename column/table | add new structure, dual read/write or copy, migrate consumers, later remove old | contract/client compatibility and rollback window |
| Change type/enum | parallel column or compatibility mapping | outlier scan, round-trip and boundary tests |
| Add unique constraint | detect and resolve duplicates before constraint | duplicate report and concurrency test |
| Add large index | assess online DDL/locking and maintenance window | query plan, duration and write-impact observation |
| Delete data/column | separate contract release after retention/backup approval | restore rehearsal and explicit approval |

## Release order

```text
backup/readiness
  -> expand schema
  -> deploy compatible application
  -> backfill with checkpoints
  -> verify counts/invariants/query plans
  -> switch reads
  -> observe
  -> contract in a later approved release
```

Prefer forward repair after a migration has reached shared environments. A file rollback is insufficient when data has already changed.

