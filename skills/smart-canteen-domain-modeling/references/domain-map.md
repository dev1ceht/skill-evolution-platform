# Smart-canteen domain map

| Context | Owns | Does not own |
| --- | --- | --- |
| Menu approval | menu draft and approval lifecycle | procurement quantities or inventory balance |
| Recipe | ingredient requirements and base quantities | current stock or purchase orders |
| Procurement planning | approved-menu demand minus usable inventory | menu approval decision or receiving persistence |
| Inventory receiving | receiving command, unit conversion, stock mutation, idempotency | recipe definition or supplier platform wire model |
| Ledger monitoring | scoped cycle, required codes, completion and current ledger alert | general alert-center disposal lifecycle |
| Alert center | normalized external/internal alert, query and disposal state | vendor authentication and raw transport model |
| Integration adapters | district platform, bright-kitchen and inspection-device protocols | core business invariants |
| Business SOP runtime | trigger, precondition, step, idempotency, evidence and adapter status | smart-canteen transaction state |

## Identity and consistency

- Use `schoolId + canteenId` on school-canteen business aggregates.
- Add the aggregate-specific identity: `menuId`, `cycleId`, `warnId`, or a business idempotency key.
- Keep uniqueness constraints aligned with domain identity; a globally unique surrogate key does not replace tenant-scoped authorization.
- Coordinate cross-context work through application services and explicit ports. Prefer eventual consistency only after defining retry, deduplication and compensation.

## Decision test

Put two rules in the same module when they share language, invariants and change cadence. Put them in separate modules when they can fail, evolve or be verified independently. Do not split solely because classes are large or merge solely because one screen calls both.
