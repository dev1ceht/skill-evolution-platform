# Skill Benchmark: frontend-api-integration

**Date**: 2026-08-14T13:21:30Z
**Evals**: 无法解析外部引用时停止生成 (1 runs each per configuration)

## Summary

| Metric | With Skill |
|--------|------------|
| Pass Rate | 50% ± 0% |

## Per-Case Results

### 无法解析外部引用时停止生成 (with_skill)

- **Pass Rate**: 50% (2/4)

| Expectation | Result | Evidence |
|-------------|--------|----------|
| expect.exit_code | ✅ | all checks passed |
| expect.files_exist | ❌ | expected file "review.md" does not exist |
| expect.files_not_exist | ✅ | all checks passed |
| expect.file_contains | ❌ | cannot read file "review.md": open C:\Users\th\AppData\Local\Temp\skill-up-1538744499\review.md: The system cannot find the file specified.; cannot read file "review.md": open C:\Users\th\AppData\Local\Temp\skill-up-1538744499\review.md: The system cannot find the file specified.; cannot read file "review.md": open C:\Users\th\AppData\Local\Temp\skill-up-1538744499\review.md: The system cannot find the file specified.; cannot read file "review.md": open C:\Users\th\AppData\Local\Temp\skill-up-1538744499\review.md: The system cannot find the file specified.; cannot read file "review.md": open C:\Users\th\AppData\Local\Temp\skill-up-1538744499\review.md: The system cannot find the file specified. |

