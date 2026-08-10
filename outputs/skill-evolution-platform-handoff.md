# Skill Evolution Platform — 交接文档

更新时间：2026-08-09  
项目目录：`D:\project\skill-evolution-platform`  
当前分支：`main`  
当前状态：工作区干净，MVP 已实现并完成测试、浏览器验收和双轴代码审查。

## 1. 接手目标

该项目用于展示两项可独立验收的实习产出：

1. 前后端接口自动化对接 Skill。
2. Skill 在线自进化与质量治理系统。

产品需求、验收条件和架构已经记录，不在本文重复展开：

- PRD：`D:\project\skill-evolution-platform\docs\PRD.md`
- 架构：`D:\project\skill-evolution-platform\docs\architecture.md`
- 领域术语：`D:\project\skill-evolution-platform\CONTEXT.md`
- 工程规范：`D:\project\skill-evolution-platform\CONTRIBUTING.md`
- 使用说明：`D:\project\skill-evolution-platform\README.md`

## 2. 当前实现状态

### 接口自动化对接

主实现位于 `src\skill_evolution\contracts.py`，当前支持：

- 解析 OpenAPI 3 JSON/YAML；
- 生成包含来源 Hash 和 JSON Pointer 的 API IR；
- 生成页面级任务计划；
- 生成 TypeScript fetch client；
- 生成调用真实 client、拦截 fetch 并断言 method/path 的 Vitest 契约测试；
- 检测接口删除、新增必填参数、参数收窄、请求/响应 Schema 不兼容等变更。

可安装 Skill 位于：

`D:\project\skill-evolution-platform\skills\frontend-api-integration`

Skill 使用渐进式披露结构：核心流程在 `SKILL.md`，详细规范在 `references`，确定性工具在 `scripts`，模板在 `assets`。

### Skill 自进化

主实现位于 `src\skill_evolution\evolution.py`，当前链路为：

`pending window → 反馈归因 → 规则级检索 → add/merge/discard/pending → staged candidate → baseline/candidate replay → promotion intent → 原子落盘 → 不可变版本/回滚`

已经处理的重要安全边界：

- 过期或已经消费的 episode 拒绝再次接收反馈；
- 低置信反馈进入 `pending`，不会强行 add；
- 候选保存来源 episode、相似规则、置信度和 Skill 基线 Hash；
- Skill 在候选生成后发生变化时，拒绝陈旧候选晋级；
- 同一候选不能重复晋级；
- 晋级前先持久化 promotion intent 和回滚内容；
- rollback 追加新版本，不修改历史版本；
- 只允许安全回滚当前 active head；
- Skill 写入被限制在配置的 Skill 根目录内；
- Judge 通过 `JudgePort` 注入，默认使用 `deterministic-v2`；
- promotion intent、Skill head、candidate、version 和 episode 状态变化均写入审计事件。

持久化适配器位于 `src\skill_evolution\repository.py`，使用 SQLite。领域层依赖 `src\skill_evolution\ports.py` 中的协议，而不是直接依赖 SQLite 实现。

### 管理后台

前端位于 `web`，后端入口位于 `src\skill_evolution\server.py` 和 `http_api.py`。

后台能够演示：

- OpenAPI 输入及 API IR、任务、Client、契约测试输出；
- pending feedback、候选决策、Replay 和晋级；
- 质量门禁分数；
- 候选、版本与审计历史；
- active 版本回滚。

## 3. 运行与验证

```powershell
cd D:\project\skill-evolution-platform
python -m pip install -e .

# 运行隔离式端到端演示
python -m skill_evolution.cli demo

# 启动管理后台
python -m skill_evolution.cli serve
# 打开 http://127.0.0.1:8765

# 完整测试
python -m pytest

# Skill 结构校验
python C:\Users\th\.codex\skills\.system\skill-creator\scripts\quick_validate.py skills\frontend-api-integration
```

最近一次验证结果：

- `10 passed`；
- Skill validator：`Skill is valid!`；
- CLI demo：解析 2 个 operation、候选决策为 merge、Replay 通过、晋级到 1.0.1；
- Playwright 浏览器验收：接口生成、反馈抽取、Replay 100/100、晋级和回滚链路均通过；
- JavaScript 和 Python 语法检查通过。

## 4. Git 基线

```text
e422c58 fix: close final audit and contract-test gaps
9d8d2b6 fix: harden evolution governance after review
fcd6727 feat: build auditable skill evolution platform
01344e1 chore: initialize project
```

代码审查从 `01344e1` 到当前 HEAD，分别检查工程标准和 PRD 完整性。第一轮发现的 SQLite 耦合、陈旧候选覆盖、重复晋级、可变回滚、低置信反馈、审计缺失和契约测试无效等问题已修复；最终复核未留下已知高/中风险项。

## 5. 后续优先级

当前是可演示 MVP，不应直接宣称已经实现“整体提效 20 倍”。下一阶段建议按以下顺序推进：

1. 建立 20～50 个真实接口任务的基准集，记录人工耗时、AI 耗时、返工和缺陷数据。
2. 将规则检索从轻量词法相似度升级为 BM25 + Embedding 混合检索。
3. 接入可配置 LLM Judge，同时继续保持编译、Schema、测试等确定性断言优先。
4. 为 SQLite 增加正式迁移机制；当前 Schema 面向新建数据库。
5. 接入真实 React/Vue 企业工程，替换演示用 fetch client 和默认目录约定。
6. 增加认证、权限、脱敏和数据保留策略后，再考虑多人或生产部署。
7. 用 P50/P90 任务耗时、成功率、回归数、Token 和延迟形成实习成果数据看板。

## 6. 注意事项

- 不要绕过 evaluation 直接写入生产 `SKILL.md`。
- 不要删除或修改历史 version；回滚必须追加新版本。
- 不要在 Skill 变化后继续晋级旧 candidate，应重新抽取和评测。
- `data/*.db`、`work`、pytest 与 Playwright 产物已被 `.gitignore` 排除。
- 若修改 Skill，运行 `quick_validate.py`，并至少 smoke test 一个脚本。
- 若修改晋级/回滚逻辑，重点回归 `tests\test_evolution_flow.py`。

## 7. Suggested skills

下一位 Agent 建议按任务选择以下 Skills：

- `$implement`：继续按照 PRD 实现下一阶段功能。
- `$tdd`：修改候选决策、版本晋级、回滚或契约 Diff 时使用测试先行。
- `$diagnosing-bugs`：处理 SQLite、HTTP 服务、浏览器交互或回归失败。
- `$code-review`：每个里程碑完成后，分别核对工程标准和 PRD。
- `$skill-creator`：修改 `frontend-api-integration` 的结构、触发描述或资源。
- `$darwin-skill`：积累真实 replay 样本后，评估和优化 Skill 质量。
- `$playwright`：验收管理后台的完整用户操作链路。

## 8. 建议的下一条任务提示词

```text
接手 D:\project\skill-evolution-platform。先阅读 README.md、docs/PRD.md、docs/architecture.md、CONTEXT.md 和本交接文档；运行完整测试和 CLI demo。然后建立真实接口任务 benchmark 数据模型与导入流程，输出 P50/P90 对接耗时、成功率、返工率和回归数，并保持现有候选晋级、审计和回滚安全边界。
```
