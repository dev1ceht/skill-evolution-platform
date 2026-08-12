# Skill Evolution Platform

一个可运行、可审计的实习项目原型，展示两项产出：

- **前后端接口自动化对接 Skill**：OpenAPI → API IR → 页面任务 → TypeScript client → 契约测试与版本 Diff。
- **Skill 在线自进化系统**：pending window → 候选抽取 → add/merge/discard → replay → 提升/回滚。

## 项目 Skills

仓库内 Skill 按研发阶段组合使用：

| 阶段 | Skill | 主要产出 |
| --- | --- | --- |
| 需求与切片 | [`smart-canteen-requirements-slicing`](skills/smart-canteen-requirements-slicing/SKILL.md) | 可追溯需求、included/deferred、阶段计划与验收条件 |
| 后端实现 | [`smart-canteen-backend`](skills/smart-canteen-backend/SKILL.md) | 领域规则、端口、Flyway、OpenAPI 与后端测试 |
| 前端对接 | [`frontend-api-integration`](skills/frontend-api-integration/SKILL.md) | API IR、任务计划、TypeScript client、契约测试与版本 Diff |
| 安全审查 | [`smart-canteen-secure-integration`](skills/smart-canteen-secure-integration/SKILL.md) | 信任边界、租户隔离、外部接入控制与滥用测试 |
| 验收发布 | [`smart-canteen-verification`](skills/smart-canteen-verification/SKILL.md) | 分层测试、fresh evidence、追溯与发布门禁 |
| 反馈演化 | [`skill-evolution-governance`](skills/skill-evolution-governance/SKILL.md) | candidate/replay/promotion/rollback 的治理证据 |

## 快速运行

```powershell
cd D:\project\skill-evolution-platform
python -m pip install -e .
python -m skill_evolution.cli demo
python -m skill_evolution.cli serve
```

浏览器访问 `http://127.0.0.1:8765`。

## 智慧食堂真实业务示例

[`examples/smart-canteen`](examples/smart-canteen/README.md) 依据智慧食堂设计文档实现了菜单审批、采购缺口、库存入库与台账预警闭环，并展示 OpenAPI 生成、Vue/Spring 对接和候选回放提升。

## 提效基准与证据

项目提供成对任务基准 CLI，输出 P50/P90、总耗时加权提速、首轮通过率、返工、缺陷和 Token 指标，同时保留输入 SHA-256 与来源引用：

```powershell
python -m skill_evolution.cli benchmark `
  --input examples/benchmarks/synthetic-sample.csv `
  --name local-demo
```

报告写入 `outputs/benchmarks/local-demo.json` 和 `outputs/benchmarks/local-demo.html`。仓库样例明确标记为 `synthetic`，不会被当作“20×”证据；完整采样规范见 [`docs/benchmark-methodology.md`](docs/benchmark-methodology.md)。

## 持续集成

GitHub Actions 在 push/PR 上执行 Python 平台与 Skill replay、Vue 测试和构建、Spring/H2 测试；三路通过后，再构建 MySQL、Redis、RabbitMQ 镜像并执行真实 MySQL 工作流。基准报告和运行验证 JSON 会上传为 Actions artifacts。

## 验证

```powershell
python -m pytest
python -m skill_evolution.cli benchmark --input examples/benchmarks/synthetic-sample.csv --name local-demo
python C:\Users\th\.codex\skills\.system\skill-creator\scripts\quick_validate.py skills\frontend-api-integration
```

## 安全边界

- 用户反馈只生成 staged candidate，不直接修改生产 Skill。
- 只有 replay 与离线评测通过的候选才能提升。
- 每次提升保存前后内容、来源、评测和哈希，可一键回滚。
- HTTP 文件服务使用固定根目录并阻止路径穿越。

详细需求见 [`docs/PRD.md`](docs/PRD.md)，架构见 [`docs/architecture.md`](docs/architecture.md)。
