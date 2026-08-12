# Delivery artifact contracts

## Requirement entry

每条结构化需求至少包含：

```yaml
- id: DOMAIN-001
  category: domain
  source:
    document: source-document-id
    section: "章节或稳定定位"
  statement: "一个可独立判断真假的业务陈述"
  acceptance:
    - "可由公开行为或确定性检查验证的条件"
  status: approved
```

使用稳定 ID；不要因措辞调整重编号。来源不明确的内容先标记 `proposed`。

## Phase plan

阶段计划至少记录：

- `phase` 与单一 `objective`
- `included` 和 `deferred`
- 数据库迁移、索引、隔离键和回滚方式
- 公开接口、内部模块边界和外部端口
- 领域不变量、幂等/并发语义
- 分层测试和环境门禁

## Traceability

每行映射一个 Requirement ID：

```text
Requirement | Implementation | Verification | Status
```

状态只描述证据支持的事实，例如 `implemented`、`port-only`、`deferred`、`blocked`。不要把接口占位符写成集成完成。

## Validation

从仓库根目录运行：

```powershell
python skills/smart-canteen-backend/scripts/validate_requirements.py docs/smart-canteen/requirements.yaml
```

阶段需求文件使用相同结构时也应执行同一校验器。

