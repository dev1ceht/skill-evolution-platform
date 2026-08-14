# Phase 1 Platform Foundation Traceability

Status: complete

| Requirement | Implementation | Verification | Status |
| --- | --- | --- | --- |
| ORG-001 | V6 organization columns, `OrganizationService`, organization REST APIs | `PlatformFoundationHttpTest`: school/canteen create, ownership validation, status and query | complete |
| IAM-001 | V6 roles/permissions/user tables, `UserAdministrationService`, dynamic role lookup, account lifecycle | `PlatformFoundationHttpTest` and `AuthModuleTest`: create, update, disable, login rejection and current roles | complete |
| AUTHZ-001 | `AuthorizationService`, `ScopeAccess`, authentication interceptor and explicit REGION/SCHOOL/CANTEEN grants | Allowed region scope succeeds; blocked, unscoped regulator and cross-scope requests return 403 | complete |
| AUDIT-001 | `audit_logs`, `AuditStore`, transactional organization/user/role writes and audit query API | Management write assertions plus `GET /api/v1/audit-logs` | complete |
| API-FOUNDATION-001 | OpenAPI, generated API IR, TypeScript client and generated contract tests | 61 operations normalized; 61 frontend contract tests pass; production build passes | complete |

## Invariants

- `app_users.role` remains the compatibility primary role; `user_roles` is the multi-role source.
- `REGULATOR` never grants global access by role alone; it requires an explicit scope grant.
- Scope authorization reads current database assignments instead of freezing them in access tokens.
- Once a user is managed through the platform scope API, database grants are authoritative; clearing them revokes the user's managed scope without falling back to an old token.
- Disabled schools and canteens remain queryable as history but are excluded from active organization lists and new canteen creation.
- Management writes and their audit records commit in the same transaction.

## Verification record

See `docs/smart-canteen/phase1-platform-foundation-verification.json` for the captured evidence and known external inputs.
