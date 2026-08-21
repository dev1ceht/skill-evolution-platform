# E-SC007-TESTS

SC-007 verification record. This file records the final full-suite, Python/SOP, governance and
code-review checks.

## Scope

- `traffic.forecast.query` reads a versioned forecast fact.
- `meal_plan.query` allocates the forecast count across a published menu with a deterministic
  largest-remainder method.
- Missing facts return unavailable results; the assistant does not create a meal plan or purchase
  plan.
- Multiple forecast generations for the same school/canteen/date/meal are retained by V33 and the
  read path selects the latest `generated_at` version.
- Missing date or meal-time clarifications are persisted without creating a business run.

## Evidence commands

| Check | Result |
|---|---|
| Targeted Java tests | Passed; the final HTTP slice covered 25 tests and service boundary covered forecast/menu/no-data cases |
| Full Java suite | Passed: 258 tests, 0 failures, 0 errors, 2 skipped; 72 Surefire report files |
| Flyway/H2 integration | Passed: migrations V31, V32 and V33 applied; V33 retains multiple forecast versions and the latest-version HTTP assertion passed |
| Python tests | Passed: 20 tests |
| SOP manifest | Passed: 12 SOPs, 1 composition and `sop-runs/menu-to-traceability.yaml` |
| Project governance validation | Passed: `{"valid": true, "issues": []}` |
| Lifecycle validation | Passed: revision 9, projectStatus `completed`, currentStage `null`, transitions 9; preserved because SC-006 had already closed the lifecycle ledger |
| Whitespace check | Passed: `git diff --cached --check` |

## Key acceptance observations

- `traffic.forecast.query` returns the stored expected count, interval, model version and source;
  no model-generated numeric forecast is used.
- `meal_plan.query` uses `PROPORTIONAL_MENU_ESTIMATE_LARGEST_REMAINDER`; the study case allocates
  850 as 283/567 and keeps the menu `PUBLISHED`.
- Missing forecast/menu facts return `available=false`; no meal-plan, procurement-plan or menu
  write is created.
- `CANTEEN_STAFF`, `SCHOOL_ADMIN` and `SYSTEM_ADMIN` can use the analysis permissions; `DINER`
  is denied.
