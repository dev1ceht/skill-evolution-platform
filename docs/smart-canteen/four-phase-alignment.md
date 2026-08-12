# Smart Canteen four-phase alignment

This file follows the user's canonical delivery order. The earlier phase2 and
phase3 plan files are retained as historical provenance; this alignment is the
current scope source of truth.

| Phase | Goal | Status | Evidence |
| --- | --- | --- | --- |
| 1 | Ledger-cycle alerts only | complete | `LedgerMonitoring`, V2, restart/concurrency paths |
| 2 | Menu approval and recipe import | complete | `MenuApproval`, `RecipeImport`, recipe module/HTTP tests |
| 3 | Procurement, inventory and unified units | complete | `ProcurementPlanning`, `InventoryReceiving`, scoped persistence |
| 4 | County platform, bright kitchen, morning inspection and alert center | first slice complete | `phase4-requirements.yaml`, `AlertCenterModuleTest`, `AlertCenterHttpTest` |

## Phase 4 boundary

The first phase-4 vertical slice is a real unified alert center. It accepts a
normalized `AlertReport`, persists it, makes repeated `(source, thirdWarnId)`
submissions idempotent, rejects changed payloads, records disposal results, and
supports paginated filtering. It exposes both REST v1 paths and the design
document compatibility paths `/alarmApi/warn/report`,
`/alarmApi/warnResult/report`, and `/alarmWarn/school/queryPage`; these are
path/normalized-input compatibility routes using the project's standard
`{code,message,data}` envelope, not the PDF's full vendor response shape.

`DistrictPlatformGateway`, `BrightKitchenGateway`, and
`MorningInspectionGateway` are replaceable ports only. Vendor HTTP clients,
camera streams, SSO, credentials, notification routing, Redis and RabbitMQ are
explicitly deferred until their external contracts and access are available.
