# E-SC006-TESTS

## Scope

SC-006 implements a read-only published-menu ingredient gap slice for canteen operations and management:

- `procurement.gap.query` resolves natural-language menu shortage questions.
- The Tool delegates to the existing scoped procurement plan service.
- Required quantities come from deterministic Recipe/BOM unit conversion and menu planned quantities.
- Inventory and open purchase-order snapshots remain business-service facts.
- The analysis does not create a procurement plan or change inventory.

## Verification

Executed on 2026-08-21 in the local study workspace:

| Check | Result |
|---|---|
| TDD red phase: focused tests before implementation | Failed at the expected missing type/service/Tool boundary |
| Focused Java tests | Passed; rule resolver, model parser/router, read-only Tool, deterministic service, authorization and Skill registry cases green |
| `AssistantControllerHttpTest` | Passed, 16 tests, including natural-language menu-to-Recipe/BOM-to-inventory and empty-menu gap paths with no-plan assertions |
| Maven full suite (`mvn -q test`) | Passed, 236 tests, 0 failures, 0 errors, 2 skipped |
| Python SOP/contract suite (`python -m pytest -q --basetemp .pytest-tmp-sc006`) | Passed, 20 tests |
| SOP validator | Passed: 10 SOPs and 1 composition run |
| Flyway/H2 migration validation | Passed: 30 migrations, including V30 `PROCUREMENT_ANALYSIS_READ` |
| Project-to-Act governance checks | Passed: project schema valid; lifecycle revision 9 valid after conditional stage-8 close |

The focused and HTTP tests verify that date/meal-time input is typed and bounded, server-derived
scope is forwarded to the business service, demand is calculated from the published menu's
estimated quantity and recipe units, and the assistant result contains shortage evidence without
creating a row in `procurement_plans`. The model remains a read-only classifier fallback; it does
not receive a business Tool or write permission.

The Spring test profile emits existing non-fatal audit-write warnings for isolated H2 fixtures;
they did not produce test failures or change the business result. No production SLO or real-provider
claim is made for this learning workspace. The first specification review also found and closed an
empty-menu summary wording gap; the regression is recorded in `E-SC006-REVIEW`.
