# Architecture

```text
Browser UI -> HTTP adapter -> Application service
                              |-> API IR / client generator
                              |-> evolution engine
                              |-> replay evaluator
                              |-> SQLite repository
                              `-> versioned Skill store

Benchmark CSV/JSON -> input validation/provenance adapter
                   -> deterministic paired-metric calculation
                   -> JSON evidence + HTML dashboard
```

The application service and benchmark CLI are public use-case boundaries. Domain functions are deterministic. Benchmark ingestion, clock capture, rendering, and atomic file writes stay in adapters. SQLite stores operational and audit state. Skill file changes are staged, evaluated, atomically promoted, and recoverable from immutable version rows.
