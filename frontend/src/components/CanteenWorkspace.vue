<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import type {
  AlertRecord,
  ConfiguredLedgerCycle,
  DailyMenu,
  DailyMenuRequest,
  Dish,
  DishRequest,
  Ingredient,
  IngredientRequest,
  IngredientUnit,
  InventoryLine,
  LedgerRecordView,
  ProcurementPlanAggregate,
  PurchaseOrder,
  Supplier,
} from '../api/generated/client';
import type { CanteenScope, SmartCanteenApiPort } from '../api/smartCanteenApi';

const props = defineProps<{
  api: SmartCanteenApiPort;
  scope: CanteenScope;
  view: string;
  roles: string[];
}>();

const ingredients = ref<Ingredient[]>([]);
const dishes = ref<Dish[]>([]);
const menus = ref<DailyMenu[]>([]);
const suppliers = ref<Supplier[]>([]);
const plans = ref<ProcurementPlanAggregate[]>([]);
const orders = ref<PurchaseOrder[]>([]);
const inventory = ref<InventoryLine[]>([]);
const units = ref<IngredientUnit[]>([]);
const unitDrafts = ref<IngredientUnit[]>([]);
const ledgerCycles = ref<ConfiguredLedgerCycle[]>([]);
const ledgerRecords = ref<LedgerRecordView[]>([]);
const alerts = ref<AlertRecord[]>([]);
const loading = ref(false);
const error = ref('');
const notice = ref('');

const ingredientKeyword = ref('');
const dishKeyword = ref('');
const selectedUnitIngredientId = ref('');
const selectedPlanId = ref('');
const selectedOrderId = ref('');
const selectedTraceCode = ref('');
const traceResult = ref<import('../api/generated/client').TraceabilityResult | null>(null);
const alertFilter = ref('');

const canWrite = computed(() => props.roles.some((role) =>
  ['SYSTEM_ADMIN', 'SCHOOL_ADMIN', 'CANTEEN_STAFF'].includes(role)));
const canApprove = computed(() => props.roles.some((role) =>
  ['SYSTEM_ADMIN', 'SCHOOL_ADMIN'].includes(role)));
const canDisposeAlert = computed(() => props.roles.some((role) =>
  ['SYSTEM_ADMIN', 'REGULATOR'].includes(role)));

const today = () => new Date().toISOString().slice(0, 10);
const dateAfter = (days: number) => {
  const value = new Date();
  value.setDate(value.getDate() + days);
  return value.toISOString().slice(0, 10);
};

const ingredientForm = reactive({
  id: '',
  name: '',
  category: '蔬菜',
  baseUnit: 'kg',
  specification: '',
  warningThreshold: 10,
  units: [{ unitCode: 'kg', baseUnit: 'kg', toBaseFactor: 1, active: true }],
});
const dishForm = reactive({
  id: '',
  name: '',
  category: '炒菜',
  description: '',
  version: 0,
  ingredients: [{ ingredientId: '', quantity: 0.1, unit: 'kg' }],
});
const menuForm = reactive({ id: '', menuDate: today(), mealTime: 'LUNCH', version: 0 });
const menuQuantities = reactive<Record<string, number>>({});
const planQuantities = reactive<Record<string, number>>({});
const planForm = reactive({ periodStart: today(), periodEnd: dateAfter(4) });
const orderSupplierId = ref('');
const orderPrices = reactive<Record<string, number>>({});
const receiveQuantities = reactive<Record<string, number>>({});
const receiveBatchNo = ref('');
const receiveExpiryDate = ref(dateAfter(7));
const stockOutForm = reactive({ ingredientId: '', quantity: 1, unit: 'kg', reason: '日常加工领用' });
const ledgerForm = reactive({ cycleId: '', ledgerCode: '', content: '', remark: '' });
const disposalContent = ref('');

const selectedPlan = computed(() => plans.value.find((item) => item.id === selectedPlanId.value) ?? plans.value[0]);
const selectedOrder = computed(() => orders.value.find((item) => item.id === selectedOrderId.value) ?? orders.value[0]);
const menuCandidates = computed(() => dishes.value.filter((item) => item.active));
const selectedMenuItems = computed(() => menuCandidates.value
  .filter((item) => (menuQuantities[item.id] ?? 0) > 0)
  .map((item, index) => ({
    dishId: item.id,
    estimatedQuantity: menuQuantities[item.id],
    sortOrder: index,
  })));

function messageOf(reason: unknown, fallback: string): string {
  return reason instanceof Error && reason.message ? reason.message : fallback;
}
function statusLabel(status: string): string {
  return ({
    DRAFT: '草稿', PENDING_APPROVAL: '待审批', APPROVED: '审批通过', REJECTED: '已驳回',
    PUBLISHED: '已公示', SUBMITTED: '已提交', CONFIRMED: '已确认', RECEIVED: '已验收',
    CANCELLED: '已取消', CONVERTED: '已生成订单', COMPLETED: '已完成', OPEN: '待处理',
    DISPOSED: '已处置',
  } as Record<string, string>)[status] ?? status;
}
function clearMessage(): void { error.value = ''; notice.value = ''; }

async function loadIngredients(): Promise<void> {
  if (!props.api.listIngredients) return;
  ingredients.value = await props.api.listIngredients(props.scope, ingredientKeyword.value || undefined);
  if (!selectedUnitIngredientId.value && ingredients.value[0]) selectedUnitIngredientId.value = ingredients.value[0].id;
}
async function loadDishes(): Promise<void> {
  if (props.api.listDishes) dishes.value = await props.api.listDishes(props.scope, dishKeyword.value || undefined);
}
async function loadMenus(): Promise<void> {
  if (props.api.listDailyMenus) menus.value = await props.api.listDailyMenus(props.scope, dateAfter(-14), dateAfter(14));
}
async function loadSuppliers(): Promise<void> {
  if (props.api.listSuppliers) {
    suppliers.value = await props.api.listSuppliers(props.scope);
    if (!orderSupplierId.value && suppliers.value[0]) orderSupplierId.value = suppliers.value[0].id;
  }
}
async function loadPlans(): Promise<void> {
  if (props.api.listProcurementPlans) {
    plans.value = await props.api.listProcurementPlans(props.scope);
    if (!selectedPlanId.value && plans.value[0]) selectedPlanId.value = plans.value[0].id;
    selectPlan(plans.value.find((item) => item.id === selectedPlanId.value) ?? plans.value[0]);
  }
}
async function loadOrders(): Promise<void> {
  if (props.api.listPurchaseOrders) {
    orders.value = await props.api.listPurchaseOrders(props.scope);
    if (!selectedOrderId.value && orders.value[0]) selectedOrderId.value = orders.value[0].id;
    selectOrder(orders.value.find((item) => item.id === selectedOrderId.value) ?? orders.value[0]);
  }
}
async function loadInventory(): Promise<void> {
  if (props.api.listInventory) {
    inventory.value = await props.api.listInventory(props.scope);
    if (!stockOutForm.ingredientId && inventory.value[0]) {
      stockOutForm.ingredientId = inventory.value[0].ingredientId;
      stockOutForm.unit = inventory.value[0].unit;
    }
  }
}
async function loadUnits(): Promise<void> {
  if (props.api.listIngredientUnits && selectedUnitIngredientId.value) {
    units.value = await props.api.listIngredientUnits(selectedUnitIngredientId.value, props.scope);
    unitDrafts.value = units.value.map((unit) => ({ ...unit }));
  }
}
async function loadLedger(): Promise<void> {
  if (props.api.ensureConfiguredLedgerCycles) {
    ledgerCycles.value = await props.api.ensureConfiguredLedgerCycles(props.scope);
  }
  if (props.api.listLedgerRecords) ledgerRecords.value = await props.api.listLedgerRecords(props.scope);
  if (ledgerCycles.value[0]) {
    ledgerForm.cycleId = ledgerCycles.value[0].cycleId;
    ledgerForm.ledgerCode = ledgerCycles.value[0].ledgerCode;
  }
}
async function loadAlerts(): Promise<void> {
  if (!props.api.queryAlerts) return;
  const result = await props.api.queryAlerts({
    schoolId: props.scope.schoolId, canteenId: props.scope.canteenId,
    pageNum: 1, pageSize: 100, ...(alertFilter.value ? { status: alertFilter.value } : {}),
  });
  alerts.value = result.records;
}
async function load(): Promise<void> {
  loading.value = true; clearMessage();
  try {
    switch (props.view) {
      case 'ingredients': await loadIngredients(); break;
      case 'units': await loadIngredients(); await loadUnits(); break;
      case 'dishes':
      case 'recipes': await Promise.all([loadIngredients(), loadDishes()]); break;
      case 'menus':
      case 'menu-approval':
      case 'menu-published': await Promise.all([loadMenus(), loadDishes()]); break;
      case 'plans': await Promise.all([loadPlans(), loadSuppliers(), loadIngredients()]); break;
      case 'orders':
      case 'receiving': await Promise.all([loadOrders(), loadSuppliers()]); break;
      case 'suppliers': await loadSuppliers(); break;
      case 'inventory':
      case 'stockout': await Promise.all([loadInventory(), loadIngredients()]); break;
      case 'ledger': await loadLedger(); break;
      case 'alerts': await loadAlerts(); break;
      default: break;
    }
  } catch (reason) { error.value = messageOf(reason, '页面数据加载失败'); }
  finally { loading.value = false; }
}

function resetIngredient(): void {
  Object.assign(ingredientForm, {
    id: '', name: '', category: '蔬菜', baseUnit: 'kg', specification: '', warningThreshold: 10,
    units: [{ unitCode: 'kg', baseUnit: 'kg', toBaseFactor: 1, active: true }],
  });
}
function editIngredient(item: Ingredient): void {
  Object.assign(ingredientForm, {
    id: item.id, name: item.name, category: item.category, baseUnit: item.baseUnit,
    specification: item.specification ?? '', warningThreshold: item.warningThreshold,
    units: [{ unitCode: item.baseUnit, baseUnit: item.baseUnit, toBaseFactor: 1, active: true }],
  });
  selectedUnitIngredientId.value = item.id;
  void loadUnits().then(() => {
    if (units.value.length) ingredientForm.units = units.value.map((unit) => ({ ...unit }));
  });
}
function addIngredientUnit(): void {
  ingredientForm.units.push({ unitCode: '', baseUnit: ingredientForm.baseUnit, toBaseFactor: 1, active: true });
}
function addUnitDraft(): void {
  const baseUnit = selectedUnitIngredientId.value
    ? ingredients.value.find((item) => item.id === selectedUnitIngredientId.value)?.baseUnit ?? 'kg'
    : 'kg';
  unitDrafts.value.push({ unitCode: '', baseUnit, toBaseFactor: 1, active: true });
}
async function saveUnits(): Promise<void> {
  if (!props.api.replaceIngredientUnits || !selectedUnitIngredientId.value) return;
  try {
    units.value = await props.api.replaceIngredientUnits(
      selectedUnitIngredientId.value,
      unitDrafts.value.filter((unit) => unit.unitCode.trim()),
      props.scope,
    );
    unitDrafts.value = units.value.map((unit) => ({ ...unit }));
    notice.value = '单位配置已保存';
  } catch (reason) { error.value = messageOf(reason, '单位配置保存失败'); }
}
async function saveIngredient(): Promise<void> {
  if (!props.api.createIngredient || !props.api.updateIngredient) return;
  clearMessage();
  try {
    const request: IngredientRequest = {
      ingredientId: ingredientForm.id || undefined,
      name: ingredientForm.name, category: ingredientForm.category, baseUnit: ingredientForm.baseUnit,
      specification: ingredientForm.specification || undefined,
      warningThreshold: Number(ingredientForm.warningThreshold),
      units: ingredientForm.units.filter((unit) => unit.unitCode.trim()),
    };
    if (ingredientForm.id) await props.api.updateIngredient(ingredientForm.id, request, props.scope);
    else await props.api.createIngredient(request, props.scope);
    notice.value = '食材保存成功'; resetIngredient(); await loadIngredients();
  } catch (reason) { error.value = messageOf(reason, '食材保存失败'); }
}

function resetDish(): void {
  Object.assign(dishForm, { id: '', name: '', category: '炒菜', description: '', version: 0 });
  dishForm.ingredients = [{ ingredientId: ingredients.value[0]?.id ?? '', quantity: 0.1, unit: ingredients.value[0]?.baseUnit ?? 'kg' }];
}
function editDish(item: Dish): void {
  Object.assign(dishForm, { id: item.id, name: item.name, category: item.category, description: item.description ?? '', version: item.version });
  dishForm.ingredients = item.ingredients.map((line) => ({ ...line }));
}
function addDishIngredient(): void {
  dishForm.ingredients.push({ ingredientId: ingredients.value[0]?.id ?? '', quantity: 0.1, unit: ingredients.value[0]?.baseUnit ?? 'kg' });
}
async function saveDish(): Promise<void> {
  if (!props.api.createDish || !props.api.updateDish) return;
  clearMessage();
  try {
    const request: DishRequest = {
      dishId: dishForm.id || undefined, name: dishForm.name, category: dishForm.category,
      description: dishForm.description || undefined, version: dishForm.version,
      ingredients: dishForm.ingredients.filter((line) => line.ingredientId && Number(line.quantity) > 0),
    };
    if (!request.ingredients.length) throw new Error('至少配置一种食材');
    if (dishForm.id) await props.api.updateDish(dishForm.id, request, props.scope);
    else await props.api.createDish(request, props.scope);
    notice.value = '菜品配方保存成功'; resetDish(); await loadDishes();
  } catch (reason) { error.value = messageOf(reason, '菜品保存失败'); }
}

function resetMenu(): void {
  Object.assign(menuForm, { id: '', menuDate: today(), mealTime: 'LUNCH', version: 0 });
  Object.keys(menuQuantities).forEach((key) => delete menuQuantities[key]);
}
function editMenu(menu: DailyMenu): void {
  Object.assign(menuForm, { id: menu.id, menuDate: menu.menuDate, mealTime: menu.mealTime, version: menu.version });
  Object.keys(menuQuantities).forEach((key) => delete menuQuantities[key]);
  menu.items.forEach((item) => { menuQuantities[item.dishId] = item.estimatedQuantity; });
}
async function saveMenu(): Promise<void> {
  if (!props.api.saveDailyMenu) return;
  try {
    if (!selectedMenuItems.value.length) throw new Error('至少选择一道菜品');
    const request: DailyMenuRequest = {
      menuId: menuForm.id || undefined, menuDate: menuForm.menuDate, mealTime: menuForm.mealTime,
      version: menuForm.version, items: selectedMenuItems.value,
    };
    await props.api.saveDailyMenu(request, props.scope);
    notice.value = '食谱草稿保存成功'; resetMenu(); await loadMenus();
  } catch (reason) { error.value = messageOf(reason, '食谱保存失败'); }
}
async function submitMenu(menu: DailyMenu): Promise<void> {
  if (!props.api.submitDailyMenu) return;
  try { await props.api.submitDailyMenu(menu.id, menu.version, props.scope); notice.value = '食谱已提交审批'; await loadMenus(); }
  catch (reason) { error.value = messageOf(reason, '提交审批失败'); }
}
async function decideMenu(menu: DailyMenu, decision: 'APPROVED' | 'REJECTED'): Promise<void> {
  if (!props.api.decideDailyMenu) return;
  const comment = typeof window !== 'undefined' ? window.prompt(decision === 'APPROVED' ? '审批意见' : '驳回原因', '') ?? '' : '';
  if (decision === 'REJECTED' && !comment.trim()) return;
  try {
    await props.api.decideDailyMenu(menu.id, { version: menu.version, decision, comment }, props.scope);
    notice.value = decision === 'APPROVED' ? '食谱审批通过' : '食谱已驳回'; await loadMenus();
  } catch (reason) { error.value = messageOf(reason, '审批操作失败'); }
}
async function publishMenu(menu: DailyMenu): Promise<void> {
  if (!props.api.publishDailyMenu) return;
  try { await props.api.publishDailyMenu(menu.id, props.scope); notice.value = '食谱已公示'; await loadMenus(); }
  catch (reason) { error.value = messageOf(reason, '食谱公示失败'); }
}

async function generatePlan(): Promise<void> {
  if (!props.api.generateProcurementPlanRange) return;
  try {
    const plan = await props.api.generateProcurementPlanRange(planForm.periodStart, planForm.periodEnd, 'plan-' + Date.now(), props.scope);
    plans.value = [plan, ...plans.value.filter((item) => item.id !== plan.id)];
    selectedPlanId.value = plan.id; selectPlan(plan); notice.value = '采购计划生成成功';
  } catch (reason) { error.value = messageOf(reason, '采购计划生成失败'); }
}
function selectPlan(plan: ProcurementPlanAggregate | undefined): void {
  selectedPlanId.value = plan?.id ?? '';
  Object.keys(planQuantities).forEach((key) => delete planQuantities[key]);
  plan?.items.forEach((item) => { planQuantities[item.ingredientId] = item.plannedBaseQuantity; });
}
async function adjustPlan(): Promise<void> {
  if (!selectedPlan.value || !props.api.adjustProcurementPlan) return;
  try {
    const plan = await props.api.adjustProcurementPlan(
      selectedPlan.value.id,
      {
        version: selectedPlan.value.version,
        items: selectedPlan.value.items.map((item) => ({
          ingredientId: item.ingredientId,
          quantity: Number(planQuantities[item.ingredientId] ?? item.plannedBaseQuantity),
          unit: item.baseUnit,
        })),
      },
      props.scope,
    );
    plans.value = plans.value.map((item) => item.id === plan.id ? plan : item);
    selectPlan(plan);
    notice.value = '采购计划调整已保存';
  } catch (reason) { error.value = messageOf(reason, '采购计划调整失败'); }
}
async function confirmPlan(): Promise<void> {
  if (!selectedPlan.value || !props.api.confirmProcurementPlan) return;
  try {
    const plan = await props.api.confirmProcurementPlan(selectedPlan.value.id, props.scope);
    plans.value = plans.value.map((item) => item.id === plan.id ? plan : item); notice.value = '采购计划已确认';
  } catch (reason) { error.value = messageOf(reason, '采购计划确认失败'); }
}
async function createOrderFromPlan(): Promise<void> {
  if (!selectedPlan.value || !props.api.createPurchaseOrderFromPlan || !orderSupplierId.value) return;
  const items = selectedPlan.value.items.filter((item) => item.plannedBaseQuantity > 0).map((item) => ({
    ingredientId: item.ingredientId, quantity: item.plannedBaseQuantity, unit: item.baseUnit,
    unitPrice: Number(orderPrices[item.ingredientId] ?? 0),
  }));
  if (!items.length) { error.value = '采购计划没有待采购项'; return; }
  try {
    await props.api.createPurchaseOrderFromPlan(selectedPlan.value.id, 'order-' + Date.now(), {
      supplierId: orderSupplierId.value, orderType: 'OFFLINE', remark: '由采购计划生成', items,
    }, props.scope);
    notice.value = '采购订单已生成'; await Promise.all([loadPlans(), loadOrders()]);
  } catch (reason) { error.value = messageOf(reason, '采购订单生成失败'); }
}
async function transitionOrder(order: PurchaseOrder, status: string): Promise<void> {
  if (!props.api.transitionPurchaseOrder) return;
  try { await props.api.transitionPurchaseOrder(order.id, status, props.scope); notice.value = '订单状态已更新'; await loadOrders(); }
  catch (reason) { error.value = messageOf(reason, '订单状态更新失败'); }
}
async function receiveSelectedOrder(): Promise<void> {
  if (!selectedOrder.value || !props.api.receivePurchaseOrder) return;
  if (!receiveBatchNo.value.trim()) { error.value = '请填写批次号'; return; }
  try {
    const items = selectedOrder.value.items
      .filter((item) => Number(receiveQuantities[item.ingredientId] ?? 0) > 0)
      .map((item) => ({
        ingredientId: item.ingredientId, quantity: Number(receiveQuantities[item.ingredientId]), unit: item.unit,
        batchNo: receiveBatchNo.value.trim(), purchasePrice: item.unitPrice,
        productionDate: today(), expiryDate: receiveExpiryDate.value,
      }));
    if (!items.length) { error.value = '至少填写一条本次验收数量'; return; }
    const result = await props.api.receivePurchaseOrder(selectedOrder.value.id, 'receipt-' + Date.now(), {
      items,
    }, props.scope);
    notice.value = '验收入库成功，生成 ' + result.traceCodes.length + ' 个溯源码';
    receiveBatchNo.value = ''; await Promise.all([loadOrders(), loadInventory()]);
  } catch (reason) { error.value = messageOf(reason, '验收入库失败'); }
}
function selectOrder(order: PurchaseOrder | undefined): void {
  selectedOrderId.value = order?.id ?? '';
  Object.keys(receiveQuantities).forEach((key) => delete receiveQuantities[key]);
  order?.items.forEach((item) => { receiveQuantities[item.ingredientId] = item.quantity; });
}
async function createSupplier(): Promise<void> {
  if (!props.api.createSupplier) return;
  const name = typeof window !== 'undefined' ? window.prompt('供应商名称', '') ?? '' : '';
  if (!name.trim()) return;
  try { await props.api.createSupplier({ name: name.trim(), active: true }, props.scope); notice.value = '供应商已保存'; await loadSuppliers(); }
  catch (reason) { error.value = messageOf(reason, '供应商保存失败'); }
}
async function stockOut(): Promise<void> {
  if (!props.api.stockOut || !stockOutForm.ingredientId) return;
  try {
    await props.api.stockOut({ reason: stockOutForm.reason, items: [{ ingredientId: stockOutForm.ingredientId, quantity: Number(stockOutForm.quantity), unit: stockOutForm.unit }] }, 'stock-out-' + Date.now(), props.scope);
    notice.value = '领用出库成功'; await loadInventory();
  } catch (reason) { error.value = messageOf(reason, '领用出库失败'); }
}
async function saveLedger(): Promise<void> {
  if (!props.api.saveLedgerRecord || !ledgerForm.cycleId || !ledgerForm.ledgerCode) return;
  try {
    await props.api.saveLedgerRecord({
      cycleId: ledgerForm.cycleId, ledgerCode: ledgerForm.ledgerCode, recordTime: new Date().toISOString(),
      content: { description: ledgerForm.content }, remark: ledgerForm.remark || undefined,
    }, props.scope);
    notice.value = '台账记录已完成'; ledgerForm.content = ''; await loadLedger();
  } catch (reason) { error.value = messageOf(reason, '台账记录保存失败'); }
}
async function disposeAlert(alert: AlertRecord): Promise<void> {
  if (!props.api.disposeAlert) return;
  const content = disposalContent.value || (typeof window !== 'undefined' ? window.prompt('填写处置结果', '') ?? '' : '');
  if (!content.trim()) return;
  try {
    await props.api.disposeAlert(alert.warnId, { processStatus: 1, processContent: content, processTime: new Date().toISOString() });
    notice.value = '预警已处置'; disposalContent.value = ''; await loadAlerts();
  } catch (reason) { error.value = messageOf(reason, '预警处置失败'); }
}
async function queryTrace(): Promise<void> {
  if (!props.api.traceability || !selectedTraceCode.value.trim()) return;
  traceResult.value = null; clearMessage();
  try { traceResult.value = await props.api.traceability(selectedTraceCode.value.trim(), props.scope); }
  catch (reason) { error.value = messageOf(reason, '未找到对应溯源码'); }
}
function setStockUnit(): void {
  const item = inventory.value.find((line) => line.ingredientId === stockOutForm.ingredientId);
  if (item) stockOutForm.unit = item.unit;
}
watch(() => props.view, () => { void load(); }, { immediate: true });
watch(selectedUnitIngredientId, () => { if (props.view === 'units') void loadUnits(); });
watch(ingredientKeyword, () => { if (props.view === 'ingredients') void loadIngredients(); });
watch(dishKeyword, () => { if (['dishes', 'recipes'].includes(props.view)) void loadDishes(); });
watch(alertFilter, () => { if (props.view === 'alerts') void loadAlerts(); });
</script>

<template>
  <section class="module-page">
    <header class="module-heading"><div><p class="eyebrow">SMART CANTEEN / {{ view.toUpperCase() }}</p><h1>{{ view === 'ingredients' ? '食材品名' : view === 'units' ? '基础单位' : view === 'dishes' ? '菜品管理' : view === 'recipes' ? '食谱编辑' : view === 'menus' ? '食谱工作台' : view === 'menu-approval' ? '待审批食谱' : view === 'menu-published' ? '已公示食谱' : view === 'plans' ? '采购计划' : view === 'orders' ? '采购订单' : view === 'receiving' ? '验收入库' : view === 'suppliers' ? '供应商' : view === 'inventory' ? '库存批次' : view === 'stockout' ? '领用出库' : view === 'ledger' ? '台账管理' : view === 'alerts' ? '预警中心' : '溯源管理' }}</h1></div><button class="ghost-button" type="button" @click="load">刷新数据</button></header>
    <p v-if="loading" class="state">正在加载业务数据…</p><p v-if="error" class="state error">{{ error }}</p><p v-if="notice" class="state success">{{ notice }}</p>

    <template v-if="view === 'ingredients'">
      <div class="workspace-grid"><section class="card"><div class="toolbar"><input v-model="ingredientKeyword" placeholder="搜索食材名称" /><button v-if="canWrite" class="primary-button" type="button" @click="resetIngredient">新建食材</button></div><table><thead><tr><th>编码</th><th>名称</th><th>分类</th><th>基础单位</th><th>阈值</th><th>操作</th></tr></thead><tbody><tr v-for="item in ingredients" :key="item.id"><td class="muted">{{ item.id }}</td><td>{{ item.name }}</td><td>{{ item.category }}</td><td>{{ item.baseUnit }}</td><td>{{ item.warningThreshold }}</td><td><button class="link-button" type="button" @click="editIngredient(item)">编辑</button></td></tr></tbody></table><p v-if="!ingredients.length && !loading" class="empty">暂无食材数据</p></section><section v-if="canWrite" class="card form-card"><h2>{{ ingredientForm.id ? '编辑食材' : '新增食材' }}</h2><div class="form-grid"><label>名称<input v-model="ingredientForm.name" /></label><label>分类<input v-model="ingredientForm.category" /></label><label>基础单位<input v-model="ingredientForm.baseUnit" /></label><label>规格<input v-model="ingredientForm.specification" /></label><label>预警阈值<input v-model.number="ingredientForm.warningThreshold" type="number" min="0" /></label></div><h3>可用单位</h3><div v-for="(unit, index) in ingredientForm.units" :key="index" class="inline-row"><input v-model="unit.unitCode" placeholder="单位" /><input v-model.number="unit.toBaseFactor" type="number" min="0.0001" step="0.0001" placeholder="换算系数" /><button class="link-button danger-link" type="button" @click="ingredientForm.units.splice(index, 1)">移除</button></div><button class="text-button" type="button" @click="addIngredientUnit">+ 添加单位</button><div class="form-actions"><button class="primary-button" type="button" @click="saveIngredient">保存</button><button class="ghost-button" type="button" @click="resetIngredient">清空</button></div></section></div>
    </template>

    <template v-else-if="view === 'units'">
      <section class="card"><div class="toolbar"><label class="compact-label">选择食材<select v-model="selectedUnitIngredientId"><option v-for="item in ingredients" :key="item.id" :value="item.id">{{ item.name }}</option></select></label><span class="muted">单位换算用于采购、验收和出库</span></div><table><thead><tr><th>单位编码</th><th>基础单位</th><th>换算系数</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="(unit, index) in unitDrafts" :key="index"><td><input v-model="unit.unitCode" /></td><td><input v-model="unit.baseUnit" /></td><td><input v-model.number="unit.toBaseFactor" type="number" min="0.0001" step="0.0001" /></td><td><input v-model="unit.active" type="checkbox" /></td><td><button class="link-button danger-link" type="button" @click="unitDrafts.splice(index, 1)">移除</button></td></tr></tbody></table><p v-if="!unitDrafts.length" class="empty">暂无单位换算配置</p><div class="form-actions"><button v-if="canWrite" class="text-button" type="button" @click="addUnitDraft">+ 添加单位</button><button v-if="canWrite" class="primary-button" type="button" @click="saveUnits">保存单位配置</button></div></section>
    </template>

    <template v-else-if="view === 'dishes' || view === 'recipes'">
      <div class="workspace-grid"><section class="card"><div class="toolbar"><input v-model="dishKeyword" placeholder="搜索菜品名称" /><button v-if="canWrite" class="primary-button" type="button" @click="resetDish">新建菜品</button></div><table><thead><tr><th>编码</th><th>菜品</th><th>分类</th><th>配方数</th><th>操作</th></tr></thead><tbody><tr v-for="item in dishes" :key="item.id"><td class="muted">{{ item.id }}</td><td>{{ item.name }}</td><td>{{ item.category }}</td><td>{{ item.ingredients.length }}</td><td><button class="link-button" type="button" @click="editDish(item)">编辑配方</button></td></tr></tbody></table><p v-if="!dishes.length" class="empty">暂无菜品数据</p></section><section v-if="canWrite" class="card form-card"><h2>{{ dishForm.id ? '编辑菜品配方' : '新建菜品配方' }}</h2><div class="form-grid"><label>菜品名称<input v-model="dishForm.name" /></label><label>分类<input v-model="dishForm.category" /></label><label class="wide">描述<textarea v-model="dishForm.description" rows="2" /></label></div><h3>配方明细</h3><div v-for="(line, index) in dishForm.ingredients" :key="index" class="recipe-row"><select v-model="line.ingredientId"><option value="" disabled>选择食材</option><option v-for="ingredient in ingredients" :key="ingredient.id" :value="ingredient.id">{{ ingredient.name }}</option></select><input v-model.number="line.quantity" type="number" min="0.0001" step="0.01" /><input v-model="line.unit" placeholder="单位" /><button class="link-button danger-link" type="button" @click="dishForm.ingredients.splice(index, 1)">移除</button></div><button class="text-button" type="button" @click="addDishIngredient">+ 添加食材</button><div class="form-actions"><button class="primary-button" type="button" @click="saveDish">保存菜品</button><button class="ghost-button" type="button" @click="resetDish">清空</button></div></section></div>
    </template>

    <template v-else-if="['menus', 'menu-approval', 'menu-published'].includes(view)">
      <div class="workspace-grid"><section class="card"><div class="toolbar"><span class="muted">食谱状态：草稿 → 待审批 → 审批通过/驳回 → 已公示</span><button v-if="canWrite && view === 'menus'" class="primary-button" type="button" @click="resetMenu">新建食谱</button></div><table><thead><tr><th>日期</th><th>餐次</th><th>状态</th><th>菜品数</th><th>操作</th></tr></thead><tbody><tr v-for="menu in menus.filter((item) => view === 'menus' || (view === 'menu-approval' ? item.status === 'PENDING_APPROVAL' : item.status === 'PUBLISHED'))" :key="menu.id"><td>{{ menu.menuDate }}</td><td>{{ menu.mealTime }}</td><td><span class="status">{{ statusLabel(menu.status) }}</span></td><td>{{ menu.items.length }}</td><td class="actions"><button v-if="menu.status === 'DRAFT' && canWrite" class="link-button" type="button" @click="editMenu(menu)">编辑</button><button v-if="menu.status === 'DRAFT' && canWrite" class="link-button" type="button" @click="submitMenu(menu)">提交</button><button v-if="menu.status === 'PENDING_APPROVAL' && canApprove" class="link-button" type="button" @click="decideMenu(menu, 'APPROVED')">通过</button><button v-if="menu.status === 'PENDING_APPROVAL' && canApprove" class="link-button danger-link" type="button" @click="decideMenu(menu, 'REJECTED')">驳回</button><button v-if="menu.status === 'APPROVED' && canApprove" class="link-button" type="button" @click="publishMenu(menu)">公示</button></td></tr></tbody></table><p v-if="!menus.length" class="empty">暂无食谱数据</p></section><section v-if="canWrite && view === 'menus'" class="card form-card"><h2>{{ menuForm.id ? '编辑食谱' : '新建食谱' }}</h2><div class="form-grid"><label>日期<input v-model="menuForm.menuDate" type="date" /></label><label>餐次<select v-model="menuForm.mealTime"><option value="BREAKFAST">早餐</option><option value="LUNCH">午餐</option><option value="DINNER">晚餐</option></select></label></div><h3>选择菜品及预计份数</h3><div class="dish-picker"><label v-for="dish in menuCandidates" :key="dish.id" class="pick-row"><input v-model.number="menuQuantities[dish.id]" type="number" min="0" step="1" placeholder="0" /><span>{{ dish.name }}</span><small>{{ dish.category }}</small></label></div><div class="form-actions"><button class="primary-button" type="button" @click="saveMenu">保存草稿</button><button class="ghost-button" type="button" @click="resetMenu">清空</button></div></section></div>
    </template>

    <template v-else-if="view === 'plans'">
      <section class="card"><div class="toolbar"><label class="compact-label">开始<input v-model="planForm.periodStart" type="date" /></label><label class="compact-label">结束<input v-model="planForm.periodEnd" type="date" /></label><button class="primary-button" type="button" @click="generatePlan">从已公示食谱生成计划</button></div><div class="two-column"><table><thead><tr><th>计划编号</th><th>周期</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="plan in plans" :key="plan.id" :class="{ selected: plan.id === selectedPlan?.id }" @click="selectPlan(plan)"><td>{{ plan.planNo }}</td><td>{{ plan.periodStart }} ~ {{ plan.periodEnd }}</td><td><span class="status">{{ statusLabel(plan.status) }}</span></td><td><button v-if="plan.status === 'DRAFT'" class="link-button" type="button" @click.stop="confirmPlan">确认</button></td></tr></tbody></table><div v-if="selectedPlan" class="detail-panel"><h2>{{ selectedPlan.planNo }}</h2><p class="muted">系统自动扣减库存和未完成订单，后端重新计算采购量；草稿状态可人工调整采购量。</p><table><thead><tr><th>食材</th><th>需求</th><th>库存</th><th>采购</th><th>调整后</th></tr></thead><tbody><tr v-for="item in selectedPlan.items" :key="item.ingredientId"><td>{{ ingredients.find((i) => i.id === item.ingredientId)?.name ?? item.ingredientId }}</td><td>{{ item.requiredBaseQuantity }} {{ item.baseUnit }}</td><td>{{ item.inventoryBaseQuantity }}</td><td>{{ item.plannedBaseQuantity }}</td><td><input v-if="selectedPlan.status === 'DRAFT'" v-model.number="planQuantities[item.ingredientId]" type="number" min="0" step="0.001" /><span v-else>{{ item.plannedBaseQuantity }}</span></td></tr></tbody></table><div class="form-actions"><button v-if="selectedPlan.status === 'DRAFT' && canWrite" class="ghost-button" type="button" @click="adjustPlan">保存调整</button><select v-model="orderSupplierId"><option value="" disabled>选择供应商</option><option v-for="supplier in suppliers" :key="supplier.id" :value="supplier.id">{{ supplier.name }}</option></select><button v-if="selectedPlan.status === 'CONFIRMED'" class="primary-button" type="button" @click="createOrderFromPlan">生成订单</button></div></div></div><p v-if="!plans.length" class="empty">暂无采购计划</p></section>
    </template>

    <template v-else-if="view === 'orders' || view === 'receiving'">
      <section class="card"><div class="toolbar"><span class="muted">订单来源于采购计划；验收成功后才生成库存批次和溯源码。</span></div><table><thead><tr><th>订单号</th><th>供应商</th><th>金额</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="order in orders" :key="order.id" :class="{ selected: order.id === selectedOrder?.id }" @click="selectOrder(order)"><td>{{ order.orderNo }}</td><td>{{ suppliers.find((s) => s.id === order.supplierId)?.name ?? order.supplierId }}</td><td>¥{{ order.totalAmount.toFixed(2) }}</td><td><span class="status">{{ statusLabel(order.status) }}</span></td><td class="actions"><button v-if="order.status === 'DRAFT'" class="link-button" type="button" @click.stop="transitionOrder(order, 'SUBMITTED')">提交</button><button v-if="order.status === 'SUBMITTED'" class="link-button" type="button" @click.stop="transitionOrder(order, 'CONFIRMED')">确认</button><button v-if="order.status === 'DRAFT' || order.status === 'SUBMITTED'" class="link-button danger-link" type="button" @click.stop="transitionOrder(order, 'CANCELLED')">取消</button></td></tr></tbody></table><p v-if="!orders.length" class="empty">暂无采购订单</p></section><section v-if="view === 'receiving' && selectedOrder" class="card receiving-card"><h2>验收 {{ selectedOrder.orderNo }}</h2><p v-if="selectedOrder.status !== 'CONFIRMED'" class="muted">订单需先提交并确认，当前状态：{{ statusLabel(selectedOrder.status) }}。</p><template v-else><div class="form-grid"><label>批次号<input v-model="receiveBatchNo" /></label><label>有效期<input v-model="receiveExpiryDate" type="date" /></label></div><table><thead><tr><th>食材</th><th>本次验收数量</th><th>单价</th></tr></thead><tbody><tr v-for="item in selectedOrder.items" :key="item.ingredientId"><td>{{ item.ingredientId }}</td><td><input v-model.number="receiveQuantities[item.ingredientId]" type="number" min="0" step="0.001" /> {{ item.unit }}</td><td>¥{{ item.unitPrice }}</td></tr></tbody></table><div class="form-actions"><button class="primary-button" type="button" @click="receiveSelectedOrder">确认验收入库</button></div></template></section></template>

    <template v-else-if="view === 'suppliers'"><section class="card"><div class="toolbar"><span class="muted">维护采购供应商主数据。</span><button v-if="canWrite" class="primary-button" type="button" @click="createSupplier">新增供应商</button></div><table><thead><tr><th>编号</th><th>供应商</th><th>联系人</th><th>电话</th><th>资质证号</th><th>状态</th></tr></thead><tbody><tr v-for="supplier in suppliers" :key="supplier.id"><td class="muted">{{ supplier.id }}</td><td>{{ supplier.name }}</td><td>{{ supplier.contactName || '-' }}</td><td>{{ supplier.contactPhone || '-' }}</td><td>{{ supplier.licenseNo || '-' }}</td><td><span class="status active">{{ supplier.active ? '启用' : '停用' }}</span></td></tr></tbody></table><p v-if="!suppliers.length" class="empty">暂无供应商</p></section></template>

    <template v-else-if="view === 'inventory' || view === 'stockout'"><section class="card"><div class="toolbar"><span class="muted">库存只由验收入库和领用出库改变。</span></div><table><thead><tr><th>食材</th><th>分类</th><th>数量</th><th>阈值</th><th>状态</th></tr></thead><tbody><tr v-for="line in inventory" :key="line.ingredientId"><td>{{ line.ingredientName }}</td><td>{{ line.category }}</td><td>{{ line.quantity }} {{ line.unit }}</td><td>{{ line.warningThreshold }}</td><td><span class="status" :class="line.warning ? 'rejected' : 'active'">{{ line.warning ? '预警' : '正常' }}</span></td></tr></tbody></table><p v-if="!inventory.length" class="empty">暂无库存</p></section><section v-if="view === 'stockout' && canWrite" class="card form-card"><h2>领用出库</h2><div class="form-grid"><label>食材<select v-model="stockOutForm.ingredientId" @change="setStockUnit"><option v-for="line in inventory" :key="line.ingredientId" :value="line.ingredientId">{{ line.ingredientName }}</option></select></label><label>数量<input v-model.number="stockOutForm.quantity" type="number" min="0.0001" /></label><label>单位<input v-model="stockOutForm.unit" /></label><label>用途<input v-model="stockOutForm.reason" /></label></div><div class="form-actions"><button class="primary-button" type="button" @click="stockOut">确认出库</button></div></section></template>

    <template v-else-if="view === 'ledger'"><div class="workspace-grid"><section class="card"><div class="toolbar"><span class="muted">按周期补录台账并保留记录。</span></div><table><thead><tr><th>周期</th><th>编码</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="cycle in ledgerCycles" :key="cycle.cycleId"><td>{{ cycle.periodStart }} ~ {{ cycle.periodEnd }}</td><td>{{ cycle.ledgerCode }}</td><td><span class="status" :class="cycle.missingLedgerCodes.length ? 'rejected' : 'active'">{{ cycle.missingLedgerCodes.length ? '待补录' : '已完成' }}</span></td><td><button class="link-button" type="button" @click="ledgerForm.cycleId = cycle.cycleId; ledgerForm.ledgerCode = cycle.ledgerCode">填写</button></td></tr></tbody></table></section><section class="card form-card"><h2>填写台账</h2><label>内容<textarea v-model="ledgerForm.content" rows="4" /></label><label>备注<textarea v-model="ledgerForm.remark" rows="2" /></label><div class="form-actions"><button class="primary-button" type="button" @click="saveLedger">保存台账</button></div></section></div><section class="card"><h2>最近记录</h2><table><thead><tr><th>编码</th><th>记录时间</th><th>状态</th><th>备注</th></tr></thead><tbody><tr v-for="record in ledgerRecords" :key="record.recordId"><td>{{ record.ledgerCode }}</td><td>{{ record.recordTime?.slice(0, 16) }}</td><td>{{ statusLabel(record.status) }}</td><td>{{ record.remark || '-' }}</td></tr></tbody></table></section></template>

    <template v-else-if="view === 'alerts'"><section class="card"><div class="toolbar"><label class="compact-label">状态<select v-model="alertFilter"><option value="">全部</option><option value="UNPROCESSED">待处理</option><option value="PROCESSED">已处置</option></select></label><span class="muted">预警处置保留原始事件和处理结果。</span></div><table><thead><tr><th>发生时间</th><th>来源</th><th>设备</th><th>内容</th><th>状态</th><th>操作</th></tr></thead><tbody><tr v-for="alert in alerts" :key="alert.warnId"><td>{{ alert.warnHappenTime?.slice(0, 16) }}</td><td>{{ alert.source }}</td><td>{{ alert.deviceName || '-' }}</td><td>{{ alert.warnContent }}</td><td><span class="status" :class="alert.processStatus === 1 ? 'active' : 'rejected'">{{ alert.processStatus === 1 ? '已处置' : '待处理' }}</span></td><td><button v-if="alert.processStatus !== 1 && canDisposeAlert" class="link-button" type="button" @click="disposeAlert(alert)">处置</button></td></tr></tbody></table><p v-if="!alerts.length" class="empty">暂无预警</p></section></template>

    <template v-else><section class="card trace-card"><div class="trace-search"><input v-model="selectedTraceCode" placeholder="输入批次溯源码" @keyup.enter="queryTrace" /><button class="primary-button" type="button" @click="queryTrace">查询溯源</button></div><p class="muted">只读展示食材、批次、供应商、订单和验收信息。</p><div v-if="traceResult" class="trace-result"><div><span>溯源码</span><strong>{{ traceResult.traceCode }}</strong></div><div><span>食材</span><strong>{{ traceResult.ingredientName || traceResult.ingredientId }}</strong></div><div><span>供应商</span><strong>{{ traceResult.supplierName || traceResult.supplierId }}</strong></div><div><span>批次</span><strong>{{ traceResult.batchId }}</strong></div><div><span>订单</span><strong>{{ traceResult.orderId }}</strong></div><div><span>数量</span><strong>{{ traceResult.quantity }} {{ traceResult.unit }}</strong></div></div><p v-else class="empty">请输入溯源码查询</p></section></template>
  </section>
</template>

<style scoped>
.module-page { display: grid; gap: 18px; }
.module-heading { display: flex; justify-content: space-between; align-items: end; gap: 20px; }
.eyebrow { margin: 0 0 6px; color: #8a6a2f; font-size: 11px; font-weight: 800; letter-spacing: .16em; }
h1 { margin: 0; color: #18302b; font-size: clamp(24px, 3vw, 34px); }
h2 { margin: 0; color: #243a34; font-size: 18px; }
h3 { margin: 18px 0 10px; color: #4b6259; font-size: 13px; }
.card { min-width: 0; padding: 22px; border: 1px solid #e5e8e1; border-radius: 16px; background: #fff; box-shadow: 0 10px 28px rgba(45, 64, 50, .05); }
.workspace-grid { display: grid; grid-template-columns: minmax(0, 1.5fr) minmax(300px, .8fr); gap: 18px; align-items: start; }
.toolbar, .form-actions, .trace-search { display: flex; align-items: center; flex-wrap: wrap; gap: 10px; }
.toolbar { justify-content: space-between; margin-bottom: 18px; }
input, select, textarea { box-sizing: border-box; width: 100%; padding: 9px 11px; border: 1px solid #d7ddd4; border-radius: 8px; color: #263c34; background: #fbfcfa; font: inherit; }
.toolbar > input { width: min(280px, 100%); }
button { border: 0; font: inherit; cursor: pointer; }
.primary-button { padding: 10px 14px; border-radius: 8px; color: #fff; background: #287e5c; font-weight: 700; }
.ghost-button { padding: 9px 13px; border: 1px solid #cad5cb; border-radius: 8px; color: #32624c; background: #fff; font-weight: 700; }
.link-button, .text-button { padding: 0; color: #2d805d; background: transparent; font-weight: 700; }
.danger-link { color: #b34e4e; }
.form-card { display: grid; gap: 12px; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.form-grid label, .form-card > label { display: grid; gap: 6px; color: #607269; font-size: 13px; font-weight: 700; }
.form-grid .wide { grid-column: 1 / -1; }
.inline-row, .recipe-row { display: grid; grid-template-columns: 1fr 1fr auto; gap: 8px; margin-bottom: 8px; }
.recipe-row { grid-template-columns: 1.4fr .7fr .7fr auto; }
.compact-label { display: flex; align-items: center; gap: 8px; color: #607269; font-size: 13px; font-weight: 700; }
.compact-label input, .compact-label select { width: auto; }
table { width: 100%; border-collapse: collapse; font-size: 13px; }
th { padding: 10px 8px; color: #7c8981; text-align: left; font-size: 12px; font-weight: 700; white-space: nowrap; border-bottom: 1px solid #edf0eb; }
td { padding: 12px 8px; color: #30473d; vertical-align: middle; border-bottom: 1px solid #f0f2ee; }
tbody tr:hover, tr.selected { background: #f4f8f3; }
.actions { display: flex; flex-wrap: wrap; gap: 8px; }
.muted, .empty { color: #819087; line-height: 1.5; }
.empty { margin: 18px 0 0; text-align: center; }
.state { margin: 0; padding: 11px 14px; border-radius: 9px; color: #65766c; background: #f2f6f1; }
.state.error { color: #9b3d3d; background: #fff1f0; }
.state.success { color: #287452; background: #eaf7ee; }
.status { display: inline-flex; padding: 4px 8px; border-radius: 999px; color: #796122; background: #fff5d8; font-size: 12px; white-space: nowrap; }
.status.active { color: #2c7652; background: #e6f5eb; }
.status.rejected { color: #a04444; background: #ffebea; }
.two-column { display: grid; grid-template-columns: minmax(0, 1fr) minmax(340px, .9fr); gap: 18px; }
.detail-panel { padding: 16px; border-radius: 12px; background: #f8faf6; }
.dish-picker { display: grid; max-height: 300px; overflow: auto; gap: 8px; }
.pick-row { display: grid; grid-template-columns: 60px 1fr auto; gap: 10px; align-items: center; padding: 8px; border-radius: 8px; background: #f7f9f5; color: #3d5549; }
.pick-row input { width: 56px; }
.pick-row small { color: #89968e; }
.receiving-card { display: grid; gap: 14px; }
.trace-card { min-height: 260px; }
.trace-search input { flex: 1 1 260px; }
.trace-result { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; margin-top: 22px; }
.trace-result div { display: grid; gap: 5px; padding: 14px; border-radius: 10px; background: #f5f8f3; }
.trace-result span { color: #7c8b81; font-size: 12px; }
.trace-result strong { color: #2d463a; word-break: break-word; }
@media (max-width: 1000px) { .workspace-grid, .two-column { grid-template-columns: 1fr; } .trace-result { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 650px) { .form-grid { grid-template-columns: 1fr; } .form-grid .wide { grid-column: auto; } .inline-row, .recipe-row { grid-template-columns: 1fr 1fr; } .trace-result { grid-template-columns: 1fr; } table { display: block; overflow-x: auto; white-space: nowrap; } }
</style>
