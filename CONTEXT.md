# Domain context

- **API contract**: an OpenAPI-compatible description supplied by a backend team.
- **API IR**: normalized operations consumed by planning, code generation, testing, and version diffing.
- **episode**: one Skill execution and the artifacts needed to attribute later feedback.
- **pending window**: a time-bounded set of episodes awaiting user feedback.
- **candidate**: a structured, source-linked proposal to improve a Skill.
- **decision**: `add`, `merge`, or `discard`; low-confidence proposals remain `pending`.
- **replay case**: a reproducible task derived from provenance and used for regression evaluation.
- **promotion**: an evaluated candidate becoming a new immutable Skill version.

