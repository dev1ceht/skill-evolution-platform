# Smart Canteen Domain Context

智慧食堂领域上下文，定义学校食堂日常运营、食品安全和监管数据共享所使用的统一业务语言。

## 组织与参与方

**School（学校）**：承担食堂运营责任的教育组织，是监管和数据归属的上级范围。
_Avoid_: Unit, Organization（除非明确指组织树节点）

**Canteen（食堂）**：学校内独立核算、采购、库存和食品安全责任的运营单元。
_Avoid_: Restaurant, Kitchen（Kitchen 只描述加工区域）

**Operator（操作人员）**：在食堂内执行采购、验收、保管、加工或台账工作的人员。
_Avoid_: Account, User（Account/User 描述登录身份，不描述业务职责）

**Supplier（供应商）**：向食堂提供食材或商品，并承担供货资质和履约责任的外部主体。
_Avoid_: Vendor

## 食谱与采购

**Ingredient（食材）**：可采购、验收、入库和领用的原材料，具有基础单位、规格和营养属性。
_Avoid_: Material, Food

**Dish（菜品）**：一个餐次中可以供应给就餐对象的成品菜，引用一个或多个食材及其标准用量。
_Avoid_: Food（Food 过于宽泛）

**Recipe（配方）**：菜品使用哪些食材、每种食材用量及适用就餐对象的定义。
_Avoid_: Menu（Menu 是按日期和餐次编排的食谱）

**Menu（食谱）**：某个食堂在指定日期和餐次提供的菜品编排及预计份数。
_Avoid_: Daily menu（中文业务名称仍统一为食谱）

**Procurement Plan（采购计划）**：根据已审批食谱、有效库存和未入库订单计算出的采购建议，不代表已经下单。
_Avoid_: Purchase Order

**Purchase Order（采购订单）**：食堂向供应商提交的正式采购承诺，包含订单明细、价格、数量和履约状态。
_Avoid_: Procurement Plan

**Receipt（验收入库）**：对供应商实际送达商品进行数量、价格、批次、日期和资质确认后形成的入库事实。
_Avoid_: Delivery（Delivery 只表示供应商送达，不表示食堂验收通过）

**Inventory Batch（库存批次）**：一次验收入库形成的可独立追踪库存单元，具有批次、生产日期、到期日期和来源订单。
_Avoid_: Inventory Item（库存汇总和批次必须区分）

**Stock-out（领用出库）**：从一个或多个库存批次扣减食材，用于加工、报损或其他明确用途的业务事实。
_Avoid_: Consumption（Consumption 可以作为统计口径，不作为操作名称）

## 合规与监管

**Ledger Cycle（台账周期）**：一段时间内一个食堂应完成的一组台账要求及其截止规则。
_Avoid_: Ledger Record（Record 是周期内某一项实际填写结果）

**Ledger Record（台账记录）**：操作人员针对某个台账要求提交的内容、照片、时间和责任人证据。
_Avoid_: Check（Check 只表示检查型台账）

**Alert（预警）**：系统或外部设备发现风险后生成的、需要跟进和处置的事件。
_Avoid_: Warning（Warning 可作为界面文案，领域对象统一使用 Alert）

**Disposal（预警处置）**：责任人对预警进行确认、说明、上传证据并关闭事件的过程。
_Avoid_: Complete（Complete 只表示动作结果，不包含处置证据）

**Traceability Code（溯源码）**：连接菜品、餐次、出库、库存批次、采购、供应商、验收和留样信息的查询标识。
_Avoid_: QR Code（二维码是溯源码的一种展示载体）

**Regulator（监管人员）**：可以跨学校按授权区域查看统计、风险、预警和整改情况的业务参与方。
_Avoid_: Admin（Admin 不代表监管范围）

## 统一范围

**Canteen Scope（食堂范围）**：由学校和食堂构成的最小运营数据归属边界。除明确的区域汇总查询外，所有食谱、采购、库存、台账、预警和溯源数据都必须绑定该范围。
