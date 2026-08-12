---
name: smart-canteen-data-migration
description: 设计、实现和验证智慧食堂 MySQL/Flyway 数据库迁移，包括租户范围键、表与索引、约束、数据回填、兼容发布、并发风险和恢复方案。Use when新增或修改表、列、索引、唯一键、外键、枚举存储、`schoolId + canteenId` 隔离、历史数据回填，或需要验证空库/升级路径和真实 MySQL 行为时。
---

# Smart Canteen Data Migration

把迁移当作一次可恢复的数据发布，而不只是能在本机执行的 SQL。历史 Flyway 文件不可变；对已有数据使用 expand → migrate → contract。

## Workflow

1. 盘点当前 Flyway 版本、生产兼容基线、相关表规模、索引/约束、读写代码和 H2/MySQL 差异。
2. 读取 `references/migration-playbook.md`，将变更分类为 additive、backfill、constraint/index、rename/type change 或 destructive contract。
3. 定义迁移前后不变量、租户范围、默认值来源、冲突处理和失败恢复。无法证明历史数据如何映射时停止，不要编造默认业务值。
4. 采用 expand → migrate → contract：先添加兼容结构，再部署兼容读写并回填，验证完成后才在后续版本收紧约束或删除旧结构。
5. 为核心业务键设计包含 `school_id + canteen_id` 的唯一约束和查询索引。验证索引列顺序对应实际过滤、排序与分页。
6. 设计可重入回填：稳定批次、检查点、幂等更新、冲突计数和速率限制。评估锁、事务日志、长事务和发布窗口。
7. 新增 Flyway 文件，不修改已发布版本。SQL 同时通过空库全迁移、从当前版本升级、H2 MySQL 模式和真实 MySQL。
8. 先验证旧应用与 expanded schema 的兼容，再验证新应用、回填、重启、重复执行保护和并发写入。
9. 记录备份/恢复、前滚修复、发布顺序、观察指标和 contract 阶段门禁。不可逆数据删除必须获得明确批准。

## Guardrails

- 不把 H2 通过当作 MySQL 语法、锁或索引行为通过。
- 不在一个不可回退发布中同时重命名字段、回填数据并删除旧列。
- 不使用全局默认食堂掩盖未知租户归属；必须有可审计映射或隔离待处理数据。
- 不通过修改旧 `V*__*.sql` 修复问题；添加新迁移前滚修复。
- 不在迁移、日志或验证产物中写真实密码和敏感数据。

## Output contract

输出 schema/data 影响分析、版本化迁移、兼容发布顺序、回填方案、索引依据、验证命令与结果、恢复/前滚方案、风险和 contract 门禁。

