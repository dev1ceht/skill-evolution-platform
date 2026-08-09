# Versioning policy

Classify changes as follows:

- **Breaking**: remove an operation, change method/path, make an optional field required, remove a response field, narrow an enum, or change authentication incompatibly.
- **Compatible**: add an operation, add an optional request field, or add a response field consumers ignore safely.
- **Review required**: change descriptions, examples, error codes, pagination, rate limits, or undocumented behavior.

Record the old and new contract hashes. For every breaking change, identify affected pages, clients, mocks, and tests, then create a migration task before updating generated code.

