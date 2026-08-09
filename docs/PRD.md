# Skill Evolution Platform MVP

## Goal

Demonstrate two independently valuable internship outputs in one auditable product:

1. Convert OpenAPI contracts into normalized API IR, frontend task plans, typed request clients, tests, and version reports.
2. Capture later user feedback, extract improvement candidates, decide add/merge/discard, replay source-linked cases, and promote or roll back Skill versions.

## Functional requirements

### R1 — API integration

- Accept an OpenAPI 3 JSON or YAML document.
- Normalize every operation into API IR with method, path, operation ID, tags, request, response, and provenance.
- Generate a page-oriented task plan and a TypeScript fetch client.
- Compare two API IR snapshots and report added, removed, and breaking operations.

### R2 — Pending feedback

- Record an episode with task, retrieved Skill/version, output summary, and expiration.
- Associate later feedback with an open episode.
- Extract at most one structured candidate per feedback event.

### R3 — Evolution decisions

- Retrieve similar existing rules.
- Decide `add`, `merge`, or `discard`, retaining low-confidence data as `pending`.
- Create a staged patch without immediately modifying the production Skill.

### R4 — Evaluation and governance

- Construct provenance-linked replay cases.
- Compare baseline and candidate with deterministic checks and an extensible judge interface.
- Promote only passing candidates, append an immutable version record, and support rollback.
- Preserve audit events for every state transition.

### R5 — Demonstration UI

- Display dashboard metrics, the integration workflow, candidates, evaluation results, versions, and audit events.
- Include one-click demo actions using bundled sample data.

## Acceptance criteria

- `python -m pytest` passes.
- `python -m skill_evolution.cli demo` completes both end-to-end flows.
- `python -m skill_evolution.cli serve` serves the UI and JSON API without external web-framework dependencies.
- The bundled Skill passes Codex `quick_validate.py`.

