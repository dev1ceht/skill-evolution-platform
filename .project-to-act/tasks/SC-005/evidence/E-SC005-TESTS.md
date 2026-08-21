# E-SC005-TESTS

## Scope

SC-005 implements the first operations/management read-only slice for inventory:

- `inventory.query` resolves natural-language inventory requests.
- The tool delegates to the existing scoped procurement/inventory service.
- Low-stock status is returned from the deterministic inventory service; it is not inferred by the model.
- `INVENTORY_READ` is granted to administrative and canteen-staff roles, while diner access is denied by the application authorization policy.
- No procurement, stock adjustment, or other write action is included in this slice.

## Verification

Executed on 2026-08-21 in the local study workspace:

| Check | Result |
|---|---|
| TDD red phase: focused tests before implementation | Failed at the expected missing-type/route compilation boundary |
| Focused Java tests | Passed; resolver, paginated tool executor, and authorization policy cases green |
| Assistant model/router tests | Passed |
| `AssistantControllerHttpTest` | Passed, 14 tests |
| Maven full suite (`mvn -q test`) | Passed, 226 tests, 0 failures, 0 errors, 2 skipped |
| Python SOP/contract suite (`python -m pytest -q --basetemp .pytest-tmp-sc005`) | Passed, 20 tests |
| Project-to-Act governance checks | Passed |

The focused tests also verify that the Tool materializes all matching service pages, rejects non-object and unknown-field input, and preserves an ingredient keyword when `warningOnly=true`. The HTTP integration test seeds a scoped inventory row with quantity `10`, warning threshold `20`, and verifies that the low-stock query returns the deterministic `warning=true` fact and the summarized count.

## Known environment note

The HTTP test profile disables the optional security filter so the controller contract can be exercised with deterministic test fixtures. Role/data-scope authorization is covered separately by `BusinessAuthorizationPolicyTest`; business services still receive the authenticated scope from the runtime context.
