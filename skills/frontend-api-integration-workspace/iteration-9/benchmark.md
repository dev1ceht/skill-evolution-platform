# Skill Benchmark: frontend-api-integration

**Date**: 2026-08-15T06:55:58Z
**Evals**: 无法解析外部引用时停止生成 (1 runs each per configuration)

## Summary

| Metric | With Skill |
|--------|------------|
| Pass Rate | 100% ± 0% |

## Per-Case Results

### 无法解析外部引用时停止生成 (with_skill)

- **Pass Rate**: 100% (7/7)

| Expectation | Result | Evidence |
|-------------|--------|----------|
| expect.exit_code | ✅ | all checks passed |
| expect.files_exist | ✅ | all checks passed |
| expect.files_not_exist | ✅ | all checks passed |
| expect.file_contains | ✅ | all checks passed |
| files_exist: [review.md] | ✅ | all required files exist |
| files_not_exist: [api-ir.json client.ts integration-plan.json] | ✅ | none of the forbidden files exist |
| output_contains{all:[external reference review.md stop common.yaml schemas.yaml]} | ✅ | output satisfies all contains checks (all:[external reference review.md stop common.yaml schemas.yaml]) |

