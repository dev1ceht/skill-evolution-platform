# Verification matrix

从仓库根目录选择受影响的最小集合；最终门禁再执行需要的广度。

| Surface | Fast check | Broader evidence |
| --- | --- | --- |
| Python platform/evolution | `python -m pytest <target>` | `python -m pytest` and `python -m skill_evolution.cli demo` |
| Skill structure | Codex `quick_validate.py <skill-dir>` | validate every changed skill and run relevant replay |
| OpenAPI generation | normalize/generate the affected contract | generated contract tests plus frontend type/build checks |
| Vue frontend | `npm test -- <target>` where supported | `npm test` and `npm run build` |
| Spring domain/HTTP | `mvn -Dtest=<tests> test` | `mvn --batch-mode test` |
| Flyway/persistence | focused H2 migration and restart tests | empty/upgrade path and real MySQL workflow |
| Docker middleware | `docker compose ... config --quiet` | `verify-stack.ps1` plus a real producer/consumer/cache workflow when implemented |
| CI definition | `tests/test_ci_workflow.py` | inspect a fresh GitHub Actions run and artifacts |
| Benchmark | CLI on a labeled fixture | paired real-task sample with hashes and provenance |

## Verification JSON fields

Record at minimum:

- change/phase identifier and capture time
- source commit or explicit dirty-worktree description
- hashed or named inputs
- runtime/tool/database/image versions that affect reproducibility
- commands with pass/fail/skip result and concise evidence
- environment-gated checks and known limits
- ephemeral database, secret file and container cleanup result

Never copy an earlier artifact and only change its timestamp.

