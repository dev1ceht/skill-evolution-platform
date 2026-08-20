---
name: smart-canteen-sop
description: 将智慧食堂菜单、采购、库存、台账、预警和食品溯源业务 SOP 封装为可触发、可执行、可验证且可审计的 Skill 流程。Use when执行或设计智慧食堂业务操作、定义前置条件和状态迁移、处理幂等重试与回滚、编排跨模块闭环、接入外部 Adapter，或需要输出 SOP 运行记录与验证证据时。
---

# Smart Canteen Business Operations

这是跨业务闭环入口。单项操作优先使用对应的业务 Skill；需要把菜单、采购、库存、台账、预警和溯源串起来时，使用本 Skill 组合一次有边界、可验证、可审计的业务运行。

## Business skill routing

- 菜单创建、审批和发布：[`canteen-menu`](../canteen-menu/SKILL.md)
- 食谱、库存到采购单和批量下单：[`canteen-order`](../canteen-order/SKILL.md)
- 收货、单位换算和批次入库：[`canteen-inventory`](../canteen-inventory/SKILL.md)
- 台账周期和缺项检查：[`canteen-ledger`](../canteen-ledger/SKILL.md)
- 食品安全事件和预警处置：[`canteen-safety`](../canteen-safety/SKILL.md)
- 订单、收货、批次到出库溯源：[`canteen-traceability`](../canteen-traceability/SKILL.md)

## Resolve the SOP

1. 读取 `docs/smart-canteen/sop-manifests.yaml`，按用户请求匹配一个 `sop` 或组合流程。
2. 读取 [sop-contract.md](references/sop-contract.md) 和匹配领域的参考文件；不要凭记忆补齐未声明的步骤、权限或外部协议。
3. 发现范围、角色、审批、单位、状态、凭据或第三方合同不明确时，停止执行并输出待确认项。

## Deployment and entry boundaries

- 当前部署只有一个固定食堂。以服务端 `SingleCanteenContext` 返回的范围为准，不提供学校/食堂切换；请求携带其他范围必须被拒绝。
- 助手入口只开放菜单查询、溯源查询和菜单发布预览/确认。采购计划、采购单、收货入库、库存出库、台账和预警处置必须从对应运营页面或结构化业务 API 执行，不能通过助手绕过页面业务门禁。
- 助手业务写入开关默认关闭；关闭助手或关闭助手业务写入只影响助手入口，不阻断运营页面的真实业务流程。

## Execute the SOP

1. 绑定服务端固定食堂上下文（持久化中的 `schoolId + canteenId` 仅用于事实归属）；查询、写入和证据都不得跨越该范围。
2. 校验当前状态和前置条件。已发布食谱、已确认订单、已处置预警和已入库批次不能被隐式覆盖。
3. 按 manifest 的步骤执行；写操作使用声明的幂等键。相同载荷重试返回原结果，同键异载荷必须失败并留下冲突证据。
4. 只在安全时重试。库存不足、单位不兼容、非法状态迁移或权限失败必须停止并保持事务回滚。
5. 外部平台、设备、对象存储和通知渠道只能通过规范化 Adapter 端口接入。缺少真实凭据或合同时，交付 port-only、模拟器或契约测试，不声称已接通。

## Compose the operational loop

按需组合以下业务 SOP，保持每一步的输入、输出和状态可追踪：

- 菜单：草稿 → 提交/审批 → 发布；发布后只读。
- 采购：已发布食谱 → 采购计划快照 → 订单 → 供应商确认 → 验收入库。
- 库存：单位换算 → 批次入库 → 预警查询 → 余额校验出库 → 溯源节点。
- 台账：创建周期 → 完成台账 → 聚合缺项 → 最后一项完成后清除预警。
- 预警：归一化上报 → 幂等接收 → 查询 → 处置；同一记录的不同处置结果不得覆盖。
- 溯源：由出库、库存批次、收货、订单和供应商事实组成可查询链路。

## Capture evidence

每次执行至少输出：

- `sop_run_id`、SOP ID、Skill 版本、输入范围和操作者角色；
- 前置校验、步骤结果、状态变化、幂等命中或冲突、回滚结果；
- 关联需求 ID、代码模块、API、迁移、测试和新鲜验证文件；
- `implemented`、`port-only`、`deferred` 和 `environment-gated` 的明确状态。

将运行记录保存为 `sop-runs/*.yaml` 或等价 JSON，并运行 `scripts/validate_sop_manifest.py` 校验 Manifest 与运行记录。运行反馈只能作为本次业务的审计和人工复盘依据，不能绕过审批直接改写业务状态或 Skill。

## Output contract

输出 SOP 选择、前置条件、执行计划或执行结果、风险/阻断项、证据路径和下一步。不能以“接口可调用”“容器 healthy”或历史 verification 文件替代业务结果证据。

## Domain references

- [sop-contract.md](references/sop-contract.md)
- [menu-approval.md](references/menu-approval.md)
- [procurement.md](references/procurement.md)
- [inventory.md](references/inventory.md)
- [ledger.md](references/ledger.md)
- [alert-disposal.md](references/alert-disposal.md)
- [traceability.md](references/traceability.md)
