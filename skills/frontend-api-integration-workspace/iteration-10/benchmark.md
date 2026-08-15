# Skill Benchmark: frontend-api-integration

**Date**: 2026-08-15T07:29:27Z
**Evals**: 生成可追溯的前端 API 集成计划, 契约存在关键歧义时停止生成, 解析参数引用并保留 wire 名称, 识别 breaking contract change 并先给出迁移计划, 在现有 HTTP 边界适配统一响应包络, 保持审批闭环中的状态转换与单位信息, 无法解析外部引用时停止生成 (1 runs each per configuration)

## Summary

| Metric | With Skill |
|--------|------------|
| Pass Rate | 52% ± 47% |

## Per-Case Results

### 生成可追溯的前端 API 集成计划 (with_skill)

- **Pass Rate**: 0% (0/0)

### 契约存在关键歧义时停止生成 (with_skill)

- **Pass Rate**: 100% (7/7)

| Expectation | Result | Evidence |
|-------------|--------|----------|
| expect.exit_code | ✅ | all checks passed |
| expect.files_exist | ✅ | all checks passed |
| expect.files_not_exist | ✅ | all checks passed |
| expect.file_contains | ✅ | all checks passed |
| files_exist: [review.md] | ✅ | all required files exist |
| files_not_exist: [api-ir.json client.ts integration-plan.json] | ✅ | none of the forbidden files exist |
| output_contains{all:[operationId pagination 确认]} | ✅ | output satisfies all contains checks (all:[operationId pagination 确认]) |

### 解析参数引用并保留 wire 名称 (with_skill)

- **Pass Rate**: 100% (5/5)

| Expectation | Result | Evidence |
|-------------|--------|----------|
| expect.exit_code | ✅ | all checks passed |
| expect.files_exist | ✅ | all checks passed |
| expect.file_contains | ✅ | all checks passed |
| files_exist: [api-ir.json client.ts client.contract.test.ts] | ✅ | all required files exist |
| output_contains{all:[encodeURIComponent Idempotency-Key resource-id client.contract.test.ts]} | ✅ | output satisfies all contains checks (all:[encodeURIComponent Idempotency-Key resource-id client.contract.test.ts]) |

### 识别 breaking contract change 并先给出迁移计划 (with_skill)

- **Pass Rate**: 67% (2/3)

| Expectation | Result | Evidence |
|-------------|--------|----------|
| expect.exit_code | ✅ | all checks passed |
| expect.files_exist | ✅ | all checks passed |
| expect.file_contains | ❌ | file "migration-plan.md" does not contain "rollback" |

### 在现有 HTTP 边界适配统一响应包络 (with_skill)

- **Pass Rate**: 0% (0/0)

### 保持审批闭环中的状态转换与单位信息 (with_skill)

- **Pass Rate**: 0% (0/0)

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

