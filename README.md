# Skill Evolution Platform

一个可运行、可审计的实习项目原型，展示两项产出：

- **前后端接口自动化对接 Skill**：OpenAPI → API IR → 页面任务 → TypeScript client → 契约测试与版本 Diff。
- **Skill 在线自进化系统**：pending window → 候选抽取 → add/merge/discard → replay → 提升/回滚。

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

## 验证

```powershell
python -m pytest
python C:\Users\th\.codex\skills\.system\skill-creator\scripts\quick_validate.py skills\frontend-api-integration
```

## 安全边界

- 用户反馈只生成 staged candidate，不直接修改生产 Skill。
- 只有 replay 与离线评测通过的候选才能提升。
- 每次提升保存前后内容、来源、评测和哈希，可一键回滚。
- HTTP 文件服务使用固定根目录并阻止路径穿越。

详细需求见 [`docs/PRD.md`](docs/PRD.md)，架构见 [`docs/architecture.md`](docs/architecture.md)。
