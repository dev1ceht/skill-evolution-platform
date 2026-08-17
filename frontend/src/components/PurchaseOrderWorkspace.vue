<script setup lang="ts">
import { onMounted, ref } from 'vue';
import type { PurchaseOrder } from '../api/generated/client';
import type { CanteenScope, SmartCanteenApiPort } from '../api/smartCanteenApi';

const props = defineProps<{
  api: SmartCanteenApiPort;
  scope: CanteenScope;
}>();

const orders = ref<PurchaseOrder[]>([]);
const loading = ref(false);
const submitting = ref(false);
const error = ref('');
const notice = ref('');

function messageOf(reason: unknown): string {
  return reason instanceof Error ? reason.message : '请求失败，请稍后重试';
}

async function load(): Promise<void> {
  if (!props.api.listPurchaseOrders) return;
  loading.value = true;
  error.value = '';
  try {
    orders.value = await props.api.listPurchaseOrders(props.scope);
  } catch (reason) {
    error.value = messageOf(reason);
  } finally {
    loading.value = false;
  }
}

function nextStatus(status: string): string | null {
  if (status === 'DRAFT') return 'SUBMITTED';
  if (status === 'SUBMITTED') return 'CONFIRMED';
  return null;
}

async function transition(order: PurchaseOrder): Promise<void> {
  const target = nextStatus(order.status);
  if (!target || !props.api.transitionPurchaseOrder) return;
  submitting.value = true;
  error.value = '';
  notice.value = '';
  try {
    const updated = await props.api.transitionPurchaseOrder(order.id, target, props.scope);
    replace(updated);
    notice.value = `${updated.orderNo} 已变更为 ${updated.status}`;
  } catch (reason) {
    error.value = messageOf(reason);
  } finally {
    submitting.value = false;
  }
}

async function receiveRemaining(order: PurchaseOrder): Promise<void> {
  if (order.status !== 'CONFIRMED' || !props.api.receivePurchaseOrder) return;
  submitting.value = true;
  error.value = '';
  notice.value = '';
  try {
    const result = await props.api.receivePurchaseOrder(
      order.id,
      `receipt:${order.id}:remaining`,
      { items: [] },
      props.scope,
    );
    notice.value = `${order.orderNo} 已验收剩余数量，溯源码 ${result.traceCodes.join('、')}`;
    await load();
  } catch (reason) {
    error.value = messageOf(reason);
  } finally {
    submitting.value = false;
  }
}

function replace(updated: PurchaseOrder): void {
  const index = orders.value.findIndex((order) => order.id === updated.id);
  if (index >= 0) orders.value.splice(index, 1, updated);
}

onMounted(load);
</script>

<template>
  <section class="workspace" data-testid="purchase-order-workspace">
    <div class="heading">
      <div>
        <p class="eyebrow">ORDER → CONFIRM → RECEIVE</p>
        <h2>采购单履约与验收</h2>
        <p class="description">采购单提交后由供应商确认；确认订单支持按剩余数量验收，验收会生成批次、库存和溯源码。</p>
      </div>
      <button type="button" class="secondary" :disabled="loading || submitting" @click="load">刷新</button>
    </div>

    <p v-if="error" class="message error" data-testid="purchase-order-error">{{ error }}</p>
    <p v-if="notice" class="message notice" data-testid="purchase-order-notice">{{ notice }}</p>
    <p v-if="loading" class="state" data-testid="purchase-order-loading">正在加载采购单…</p>
    <p v-else-if="!orders.length" class="state" data-testid="purchase-order-empty">暂无采购单</p>
    <div v-else class="orders">
      <article v-for="order in orders" :key="order.id" class="order-card">
        <div class="order-heading">
          <div>
            <span class="status">{{ order.status }}</span>
            <h3>{{ order.orderNo }}</h3>
          </div>
          <strong>{{ order.totalAmount.toFixed(2) }}</strong>
        </div>
        <p class="muted">供应商：{{ order.supplierId }} · 类型：{{ order.orderType }}</p>
        <ul>
          <li v-for="item in order.items" :key="item.ingredientId">
            {{ item.ingredientId }}：{{ item.quantity }} {{ item.unit }} × {{ item.unitPrice.toFixed(2) }}
          </li>
        </ul>
        <div class="actions">
          <button
            v-if="nextStatus(order.status)"
            type="button"
            :disabled="submitting"
            @click="transition(order)"
          >
            {{ nextStatus(order.status) === 'SUBMITTED' ? '提交供应商确认' : '确认供应商订单' }}
          </button>
          <button
            v-if="order.status === 'CONFIRMED'"
            data-testid="purchase-order-receive"
            type="button"
            :disabled="submitting"
            @click="receiveRemaining(order)"
          >
            验收剩余数量并入库
          </button>
        </div>
      </article>
    </div>
  </section>
</template>

<style scoped>
.workspace { margin: 24px 0; padding: 28px; border: 1px solid #d6e0d7; border-radius: 20px; background: rgba(255,255,255,.88); box-shadow: 0 16px 48px rgba(30,69,47,.08); }
.heading, .order-heading, .actions { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.eyebrow { margin: 0 0 6px; color: #217a55; font-size: 11px; font-weight: 800; letter-spacing: .18em; }
h2, h3 { margin: 0; }
h2 { font-size: 28px; }
h3 { margin-top: 8px; font-size: 22px; }
.description, .state, .muted { color: #69786f; }
.message { margin: 14px 0; padding: 12px 15px; border-radius: 10px; }
.error { color: #8c2525; background: #feecec; }
.notice { color: #17623f; background: #e1f4e8; }
.orders { display: grid; grid-template-columns: repeat(auto-fit, minmax(280px, 1fr)); gap: 14px; margin-top: 22px; }
.order-card { padding: 18px; border: 1px solid #d6e0d7; border-radius: 14px; background: #f8fbf8; }
.status { display: inline-block; padding: 4px 8px; border-radius: 99px; color: #17623f; background: #e1f4e8; font-size: 11px; font-weight: 800; }
button { padding: 10px 14px; border: 0; border-radius: 9px; color: white; background: #217a55; font-weight: 700; cursor: pointer; }
button:disabled { cursor: not-allowed; opacity: .45; }
button.secondary { color: #217a55; border: 1px solid #c7d6ca; background: white; }
ul { padding-left: 20px; color: #506158; line-height: 1.8; }
</style>
