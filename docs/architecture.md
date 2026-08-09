# Architecture

```text
Browser UI -> HTTP adapter -> Application service
                              |-> API IR / client generator
                              |-> evolution engine
                              |-> replay evaluator
                              |-> SQLite repository
                              `-> versioned Skill store
```

The application service is the public use-case boundary. Domain functions are deterministic. SQLite stores operational and audit state. Skill file changes are staged, evaluated, atomically promoted, and recoverable from immutable version rows.

