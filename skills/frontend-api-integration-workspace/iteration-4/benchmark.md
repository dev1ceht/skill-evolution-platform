# Skill Benchmark: frontend-api-integration

**Date**: 2026-08-14T13:40:49Z
**Evals**: 无法解析外部引用时停止生成 (1 runs each per configuration)

## Summary

| Metric | With Skill |
|--------|------------|
| Pass Rate | 86% ± 0% |

## Per-Case Results

### 无法解析外部引用时停止生成 (with_skill)

- **Pass Rate**: 86% (6/7)

| Expectation | Result | Evidence |
|-------------|--------|----------|
| expect.exit_code | ✅ | all checks passed |
| expect.files_exist | ✅ | all checks passed |
| expect.files_not_exist | ✅ | all checks passed |
| expect.file_contains | ✅ | all checks passed |
| files_exist: [review.md] | ✅ | all required files exist |
| files_not_exist: [api-ir.json client.ts integration-plan.json] | ✅ | none of the forbidden files exist |
| output_contains.all: missing [external reference stop common.yaml schemas.yaml] | ❌ | output does not contain required keywords: [external reference stop common.yaml schemas.yaml] |

