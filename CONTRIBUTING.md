# Engineering standards

- Keep domain logic independent from HTTP and SQLite adapters.
- Validate all identifiers and confine file writes to the configured Skill root.
- Test behavior through public service and HTTP boundaries.
- Prefer deterministic assertions over model-based judging.
- Store provenance for every candidate, decision, evaluation, and version.
- Never overwrite a production Skill before evaluation passes; use atomic writes and retain rollback content.

