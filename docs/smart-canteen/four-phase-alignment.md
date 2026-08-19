# Smart Canteen four-phase alignment

This file follows the user's canonical delivery order. The earlier phase2 and
phase3 plan files are retained as historical provenance; this alignment is the
current scope source of truth.

| Phase | Goal | Status | Evidence |
| --- | --- | --- | --- |
| 1 | Ledger governance and cycle alerts | complete | `ConfigurableLedgerService`, V2/V8, restart/concurrency paths |
| 2 | Canonical daily-menu approval | complete | `DailyMenuService`, V12/V25/V27, menu module/HTTP tests |
| 3 | Canonical procurement, inventory and units | complete | `ProcurementOperationsService`, `JdbcOperationalStore`, scoped persistence |
| 4 | County platform, bright kitchen, morning inspection and alert center | first slice complete | `phase4-requirements.yaml`, `AlertCenterModuleTest`, `AlertCenterHttpTest` |

## Phase 4 boundary

The first phase-4 vertical slice is a real unified alert center. It accepts a
normalized `AlertReport`, persists it, makes repeated `(source, thirdWarnId)`
submissions idempotent, rejects changed payloads, records disposal results, and
supports paginated filtering. It exposes the canonical REST v1 paths using the
project's standard `{code,message,data}` envelope. The former design-document
aliases are no longer registered; vendor-specific payloads must be normalized
by an adapter before entering this API.

`DistrictPlatformGateway`, `BrightKitchenGateway`, and
`MorningInspectionGateway` are replaceable ports only. Vendor HTTP clients,
camera streams, SSO, credentials, notification routing, Redis and RabbitMQ are
explicitly deferred until their external contracts and access are available.
