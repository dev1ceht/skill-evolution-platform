---
name: frontend-api-integration
description: Parse OpenAPI 3 JSON or YAML contracts, normalize them into API IR, plan page-level frontend integration tasks, generate typed TypeScript clients, design mock/contract/integration/E2E tests, and detect breaking API changes. Use when integrating backend endpoints into React or Vue pages, reviewing an API contract before development, migrating between interface versions, generating request-layer code, or planning frontend-backend joint debugging and regression work.
---

# Frontend API Integration

Convert an API contract into reviewable frontend code and verification artifacts. Preserve provenance from every generated operation back to its source contract.

## Workflow

1. Locate the API source and the target frontend page or feature.
2. Read `references/api-ir.md`, then normalize OpenAPI JSON/YAML with `scripts/normalize_openapi.py`.
3. Stop and report ambiguities when the contract omits an operation ID, success schema, authentication requirement, or pagination mode that cannot be inferred safely.
4. Group operations by page behavior and produce a task plan containing dependencies, loading/error/empty states, mock work, and verification work.
5. Read `references/frontend-standards.md`, then generate or adapt the typed client with `scripts/generate_client.py`.
6. Read `references/testing-policy.md`, generate client behavior tests with `scripts/generate_contract_tests.py`, and run the smallest applicable test matrix before broader regression tests.
7. When an old contract exists, normalize both contracts and run `scripts/diff_contracts.py`. Read `references/versioning-policy.md` before proposing migrations.
8. Report generated files, unresolved assumptions, breaking changes, test results, and measured human review time.

## Guardrails

- Treat the contract as evidence, not unquestionable truth; compare it with observed responses when fixtures or logs exist.
- Never invent required fields, authentication, error codes, or pagination behavior.
- Keep generated request code behind the repository's existing API/data-access boundary.
- Reuse existing HTTP clients, interceptors, error mapping, query keys, and test utilities.
- Do not modify shared infrastructure or accept breaking changes without explicit approval.
- Generate a staged change and review the diff before writing across multiple pages.
- Prefer deterministic schema, compile, and test checks over subjective assessment.

## Output contract

Produce these artifacts as applicable:

- `api-ir.json` with source document hash and JSON pointers.
- `integration-plan.json` grouped by target page and dependency.
- Typed client, hooks/adapters, mock fixtures, and test changes.
- `contract-diff.json` with added, removed, changed, and breaking items.
- A concise handoff containing assumptions, test evidence, and remaining manual work.

## Learned rules

- Detect `page`, `offset`, and `cursor` pagination from explicit contract fields; request confirmation when the mode is ambiguous.

