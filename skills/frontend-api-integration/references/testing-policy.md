# Test policy

Apply the smallest reliable matrix:

1. Validate the source contract and normalized API IR.
2. Run type checking and linting on generated code.
3. Add contract tests for method, path, parameters, request body, success schema, and documented errors.
4. Add mock tests for loading, success, empty, and error page states.
5. Add integration tests at the page data boundary for high-value flows.
6. Add E2E tests only for critical cross-system behavior.

Test public boundaries rather than generator internals. Use real contract fixtures; do not reproduce the implementation algorithm in assertions.

