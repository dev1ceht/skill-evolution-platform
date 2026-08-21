# SC-005 学习环境交付说明

## 交付范围

SC-005 只交付 `inventory.query` 只读助手能力：运营人员和管理者可以在服务端食堂范围内查询库存、按食材名称过滤并识别库存服务计算出的低库存标记。员工/学生不获得 `INVENTORY_READ`。

本文件是个人学习环境的交付记录，不构成真实企业、学校、医院或园区生产发布批准。当前不接入真实 ERP、POS、采购系统或外部模型 Provider。

## 已验证项目

- Java 全量回归、HTTP 助手链路、Python SOP 校验和 Project-to-Act 校验通过；详见 `E-SC005-TESTS`。
- Spec/Standards 双轴复审通过；详见 `E-SC005-REVIEW`。
- Skill manifest、OpenAPI schema、`INVENTORY_READ` 迁移和 Agent Runtime 路径已同步。
- 只读查询通过现有 `ProcurementOperationsService`，不允许 Agent 直接访问数据库或执行库存写入。

## 学习环境运行边界

- 性能目标沿用 Skill 的 3000ms deadline；本切片没有宣称生产 SLO、容量或可用性指标。
- 本地数据可以使用仓库中的 study dataset；若库存数据发生变化，测试数据库可重新创建并运行 Flyway 迁移。
- 当前没有灰度发布、线上监控和真实业务审批人；这些是接入真实业务前的前置条件。

## 回退方案

在学习环境中，回退 SC-005 变更即可移除助手库存入口和 `INVENTORY_READ` 迁移；既有库存入库、出库和采购状态机不在本切片内。若本地数据库已执行 V29，使用测试数据库重建或按项目既有数据库初始化流程重建，不对真实数据库执行人工删改。

## 后续进入真实集成的条件

1. 接入并验证真实库存业务 API，保留服务端 scope 和业务鉴权。
2. 补充真实或本地可复现的 AgentScope Provider 烟囱测试。
3. 为库存查询补充真实容量、延迟、监控、灰度和回滚证据。
4. 在新增 BOM 缺口、采购建议或采购写入前，重新建立审批和验收边界。
