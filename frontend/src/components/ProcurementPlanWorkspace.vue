<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import type {
  AdjustProcurementPlanRequest,
  CreateProcurementOrderRequest,
  ProcurementPlanAggregate,
  Supplier,
} from '../api/generated/client';
import type { CanteenScope, SmartCanteenApiPort } from '../api/smartCanteenApi';

const props = defineProps<{
  api: SmartCanteenApiPort;
  scope: CanteenScope;
}>();

const plans = ref<ProcurementPlanAggregate[]>([]);
const suppliers = ref<Supplier[]>([]);
const selectedPlanId = ref('');
const periodStart = ref(localDate());
const periodEnd = ref(localDate());
const supplierId = ref('');
const orderType = ref('OFFLINE');
const loading = ref(false);
const submitting = ref(false);
const error = ref('');
const notice = ref('');
const adjustedQuantities = ref<Record<string, number>>({});
const unitPrices = ref<Record<string, number>>({});

const selectedPlan = computed(() =>
  plans.value.find((plan) => plan.id === selectedPlanId.value) ?? null,
);
const canAdjust = computed(() => selectedPlan.value?.status === 'DRAFT');
const canConfirm = computed(() => selectedPlan.value?.status === 'DRAFT');
const canCancel = computed(() => selectedPlan.value?.status === 'DRAFT' || selectedPlan.value?.status === 'CONFIRMED');
const canCreateOrder = computed(() => selectedPlan.value?.status === 'CONFIRMED');

function localDate(): string {
  const now = new Date();
  const offset = now.getTimezoneOffset() * 60_000;
  return new Date(now.getTime() - offset).toISOString().slice(0, 10);
}

function messageOf(reason: unknown): string {
  return reason instanceof Error ? reason.message : '请求失败，请稍后重试';
}

function newIdempotencyKey(prefix: string): string {
  const random = typeof globalThis.crypto?.randomUUID === 'function'
    ? globalThis.crypto.randomUUID()
    : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
  return `${prefix}:${random}`;
}

function replacePlan(next: ProcurementPlanAggregate): void {
  const index = plans.value.findIndex((plan) => plan.id === next.id);
  if (index >= 0) {
    plans.value.splice(index, 1, next);
  } else {
    plans.value.unshift(next);
  }
  selectedPlanId.value = next.id;
  adjustedQuantities.value = Object.fromEntries(
    next.items.map((item) => [item.ingredientId, item.plannedBaseQuantity]),
  );
  unitPrices.value = Object.fromEntries(
    next.items.map((item) => [item.ingredientId, unitPrices.value[item.ingredientId] ?? 0]),
  );
}

async function load(): Promise<void> {
  loading.value = true;
  error.value = '';
  try {
    const [loadedPlans, loadedSuppliers] = await Promise.all([
      props.api.listProcurementPlans
        ? props.api.listProcurementPlans(props.scope)
        : Promise.resolve([]),
      props.api.listSuppliers
        ? props.api.listSuppliers(props.scope)
        : Promise.resolve([]),
    ]);
    plans.value = loadedPlans;
    suppliers.value = loadedSuppliers.filter((supplier) => supplier.active);
    if (!selectedPlanId.value && loadedPlans.length > 0) {
      replacePlan(loadedPlans[0]);
    }
    if (!supplierId.value && suppliers.value.length > 0) {
      supplierId.value = suppliers.value[0].id;
    }
  } catch (reason) {
    error.value = messageOf(reason);
  } finally {
    loading.value = false;
  }
}

function choosePlan(plan: ProcurementPlanAggregate): void {
  selectedPlanId.value = plan.id;
  adjustedQuantities.value = Object.fromEntries(
    plan.items.map((item) => [item.ingredientId, item.plannedBaseQuantity]),
  );
  unitPrices.value = Object.fromEntries(
    plan.items.map((item) => [item.ingredientId, unitPrices.value[item.ingredientId] ?? 0]),
  );
  notice.value = '';
  error.value = '';
}

async function generate(): Promise<void> {
  if (!props.api.generateProcurementPlanRange) return;
  if (!periodStart.value || !periodEnd.value || periodEnd.value < periodStart.value) {
    error.value = '计划结束日期不能早于开始日期';
    return;
  }
  submitting.value = true;
  error.value = '';
  notice.value = '';
  try {
    const key = newIdempotencyKey(
      `procurement-plan:${props.scope.schoolId}:${props.scope.canteenId}:${periodStart.value}:${periodEnd.value}`,
    );
    const plan = await props.api.generateProcurementPlanRange(
      periodStart.value,
      periodEnd.value,
      key,
      props.scope,
    );
    replacePlan(plan);
    notice.value = plan.items.length
      ? '已按已发布食谱、库存和未收货订单生成缺口计划'
      : '当前周期没有需要采购的食材';
  } catch (reason) {
    error.value = messageOf(reason);
  } finally {
    submitting.value = false;
  }
}

async function adjust(): Promise<void> {
  if (!selectedPlan.value || !props.api.adjustProcurementPlan) return;
  const request: AdjustProcurementPlanRequest = {
    version: selectedPlan.value.version,
    items: selectedPlan.value.items.map((item) => ({
      ingredientId: item.ingredientId,
      quantity: Number(adjustedQuantities.value[item.ingredientId] ?? item.plannedBaseQuantity),
      unit: item.baseUnit,
    })),
  };
  if (request.items.some((item) => !Number.isFinite(item.quantity) || item.quantity < 0)) {
    error.value = '计划数量必须是大于等于 0 的数字';
    return;
  }
  submitting.value = true;
  error.value = '';
  notice.value = '';
  try {
    replacePlan(await props.api.adjustProcurementPlan(
      selectedPlan.value.id,
      request,
      props.scope,
    ));
    notice.value = '采购计划数量已保存';
  } catch (reason) {
    error.value = messageOf(reason);
  } finally {
    submitting.value = false;
  }
}

async function confirm(): Promise<void> {
  if (!selectedPlan.value || !props.api.confirmProcurementPlan) return;
  submitting.value = true;
  error.value = '';
  notice.value = '';
  try {
    replacePlan(await props.api.confirmProcurementPlan(selectedPlan.value.id, props.scope));
    notice.value = '采购计划已确认，可以生成采购单';
  } catch (reason) {
    error.value = messageOf(reason);
  } finally {
    submitting.value = false;
  }
}

async function cancel(): Promise<void> {
  if (!selectedPlan.value || !props.api.cancelProcurementPlan) return;
  submitting.value = true;
  error.value = '';
  notice.value = '';
  try {
    replacePlan(await props.api.cancelProcurementPlan(selectedPlan.value.id, props.scope));
    notice.value = '采购计划已取消';
  } catch (reason) {
    error.value = messageOf(reason);
  } finally {
    submitting.value = false;
  }
}

async function createOrder(): Promise<void> {
  if (!selectedPlan.value || !props.api.createPurchaseOrderFromPlan) return;
  const items = selectedPlan.value.items
    .map((item) => ({
      ingredientId: item.ingredientId,
      quantity: item.plannedBaseQuantity,
      unit: item.baseUnit,
      unitPrice: Number(unitPrices.value[item.ingredientId] ?? 0),
    }))
    .filter((item) => item.quantity > 0);
  if (!supplierId.value) {
    error.value = '请选择供应商';
    return;
  }
  if (items.some((item) => !Number.isFinite(item.unitPrice) || item.unitPrice < 0)) {
    error.value = '采购单价必须是大于等于 0 的数字';
    return;
  }
  if (!items.length) {
    error.value = '采购计划没有可下单的食材';
    return;
  }
  const request: CreateProcurementOrderRequest = {
    supplierId: supplierId.value,
    orderType: orderType.value,
    items,
  };
  submitting.value = true;
  error.value = '';
  notice.value = '';
  try {
    const key = `procurement-order:${selectedPlan.value.id}`;
    const order = await props.api.createPurchaseOrderFromPlan(
      selectedPlan.value.id,
      key,
      request,
      props.scope,
    );
    const linkedPlan = plans.value.find((plan) => plan.id === selectedPlan.value?.id);
    if (linkedPlan) {
      linkedPlan.status = 'CONVERTED';
      linkedPlan.orderIds = [...new Set([...linkedPlan.orderIds, order.id])];
    }
    notice.value = `采购单 ${order.orderNo} 已创建，后续可在采购单页面提交并验收入库`;
  } catch (reason) {
    error.value = messageOf(reason);
  } finally {
    submitting.value = false;
  }
}

onMounted(load);
</script>

<template>
  <section class="workspace" data-testid="procurement-plan-workspace">
    <div class="heading">
      <div>
        <p class="eyebrow">RECIPE → PLAN → ORDER</p>
        <h2>统一食谱与采购计划</h2>
        <p class="description">计划只读取已发布食谱，自动扣除库存和未收货订单，保存计算快照后再由人工确认。</p>
      </div>
      <button type="button" class="secondary" :disabled="loading || submitting" @click="load">刷新</button>
    </div>

    <p v-if="error" class="message error" data-testid="procurement-plan-error">{{ error }}</p>
    <p v-if="notice" class="message notice" data-testid="procurement-plan-notice">{{ notice }}</p>

    <div class="generate-bar">
      <label>开始日期 <input v-model="periodStart" type="date" /></label>
      <label>结束日期 <input v-model="periodEnd" type="date" /></label>
      <button data-testid="procurement-plan-generate" type="button" :disabled="submitting" @click="generate">生成统一采购计划</button>
    </div>

    <p v-if="loading" class="state" data-testid="procurement-plan-loading">正在加载采购计划…</p>
    <p v-else-if="!plans.length" class="state" data-testid="procurement-plan-empty">暂无采购计划</p>
    <div v-else class="content-grid">
      <nav class="plan-list" aria-label="采购计划列表">
        <button
          v-for="plan in plans"
          :key="plan.id"
          type="button"
          class="plan-entry"
          :class="{ selected: plan.id === selectedPlanId }"
          @click="choosePlan(plan)"
        >
          <strong>{{ plan.planNo }}</strong>
          <span>{{ plan.periodStart }} 至 {{ plan.periodEnd }}</span>
          <em>{{ plan.status }}</em>
        </button>
      </nav>

      <article v-if="selectedPlan" class="plan-detail">
        <div class="detail-heading">
          <div>
            <span class="status">{{ selectedPlan.status }}</span>
            <h3>{{ selectedPlan.planNo }}</h3>
          </div>
          <small>版本 {{ selectedPlan.version }} · 来源菜单 {{ selectedPlan.sourceMenuIds.length }} 个</small>
        </div>

        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>食材</th>
                <th>食谱需求</th>
                <th>库存</th>
                <th>未收货订单</th>
                <th>缺口</th>
                <th>计划采购</th>
                <th v-if="canCreateOrder">单价</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in selectedPlan.items" :key="item.ingredientId">
                <td>{{ item.ingredientId }}</td>
                <td>{{ item.requiredBaseQuantity }} {{ item.baseUnit }}</td>
                <td>{{ item.inventoryBaseQuantity }} {{ item.baseUnit }}</td>
                <td>{{ item.openOrderBaseQuantity }} {{ item.baseUnit }}</td>
                <td>{{ item.shortageBaseQuantity }} {{ item.baseUnit }}</td>
                <td>
                  <input
                    v-model.number="adjustedQuantities[item.ingredientId]"
                    type="number"
                    min="0"
                    step="0.0001"
                    :disabled="!canAdjust || submitting"
                    :aria-label="`${item.ingredientId} 计划采购量`"
                  />
                  <span>{{ item.baseUnit }}</span>
                </td>
                <td v-if="canCreateOrder">
                  <input
                    v-model.number="unitPrices[item.ingredientId]"
                    type="number"
                    min="0"
                    step="0.01"
                    :disabled="submitting"
                    :aria-label="`${item.ingredientId} 单价`"
                  />
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="actions">
          <button data-testid="procurement-plan-adjust" type="button" class="secondary" :disabled="submitting || !canAdjust" @click="adjust">保存数量调整</button>
          <button data-testid="procurement-plan-confirm" type="button" :disabled="submitting || !canConfirm" @click="confirm">确认采购计划</button>
          <button data-testid="procurement-plan-cancel" type="button" class="danger" :disabled="submitting || !canCancel" @click="cancel">取消采购计划</button>
        </div>

        <div v-if="canCreateOrder" class="order-form">
          <h4>生成采购单</h4>
          <label>
            供应商
            <select v-model="supplierId">
              <option value="" disabled>请选择供应商</option>
              <option v-for="supplier in suppliers" :key="supplier.id" :value="supplier.id">
                {{ supplier.name }}（{{ supplier.id }}）
              </option>
            </select>
          </label>
          <label>
            订单类型
            <select v-model="orderType">
              <option value="OFFLINE">线下采购</option>
              <option value="ONLINE">线上采购</option>
            </select>
          </label>
          <button data-testid="procurement-order-create" type="button" :disabled="submitting" @click="createOrder">生成采购单</button>
        </div>
      </article>
    </div>
  </section>
</template>

<style scoped>
.workspace { margin: 24px 0; padding: 28px; border: 1px solid #d6e0d7; border-radius: 20px; background: rgba(255,255,255,.88); box-shadow: 0 16px 48px rgba(30,69,47,.08); }
.heading, .detail-heading, .generate-bar, .actions, .order-form { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.heading, .detail-heading { justify-content: space-between; }
.eyebrow { margin: 0 0 6px; color: #217a55; font-size: 11px; font-weight: 800; letter-spacing: .18em; }
h2, h3, h4 { margin: 0; }
h2 { font-size: 28px; }
h3 { margin-top: 8px; font-size: 22px; }
h4 { flex-basis: 100%; }
.description, .state, small { color: #69786f; }
.generate-bar { margin: 24px 0; padding: 16px; border-radius: 14px; background: #f1f6f1; }
label { display: grid; gap: 5px; color: #506158; font-size: 13px; font-weight: 700; }
input, select { min-width: 120px; padding: 9px 10px; border: 1px solid #c7d6ca; border-radius: 8px; background: white; color: #17231d; }
button { padding: 10px 14px; border: 0; border-radius: 9px; color: white; background: #217a55; font-weight: 700; cursor: pointer; }
button:disabled { cursor: not-allowed; opacity: .45; }
button.secondary { color: #217a55; border: 1px solid #c7d6ca; background: white; }
button.danger { color: #8c2525; border: 1px solid #e8bcbc; background: #fff7f7; }
.message { margin: 14px 0; padding: 12px 15px; border-radius: 10px; }
.error { color: #8c2525; background: #feecec; }
.notice { color: #17623f; background: #e1f4e8; }
.content-grid { display: grid; grid-template-columns: minmax(210px, .3fr) minmax(0, 1fr); gap: 18px; }
.plan-list { display: grid; align-content: start; gap: 8px; }
.plan-entry { display: grid; gap: 5px; text-align: left; color: #17231d; border: 1px solid #d6e0d7; background: #f8fbf8; }
.plan-entry.selected { border-color: #217a55; background: #e6f3eb; }
.plan-entry span, .plan-entry em { color: #69786f; font-size: 12px; font-style: normal; }
.plan-detail { min-width: 0; }
.status { display: inline-block; padding: 4px 8px; border-radius: 99px; color: #17623f; background: #e1f4e8; font-size: 11px; font-weight: 800; }
.table-wrap { margin: 20px 0; overflow-x: auto; }
table { width: 100%; border-collapse: collapse; font-size: 13px; }
th, td { padding: 10px 8px; border-bottom: 1px solid #e4ebe5; text-align: left; white-space: nowrap; }
th { color: #69786f; font-size: 12px; }
td input { width: 104px; min-width: 0; }
td span { margin-left: 4px; color: #69786f; }
.order-form { margin-top: 24px; padding: 16px; border-radius: 14px; background: #f1f6f1; }
@media (max-width: 800px) { .content-grid { grid-template-columns: 1fr; } }
</style>
