<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import type {
  CanteenScope,
  DinerMenu,
  DinerMenuItem,
  MealOrder,
  SmartCanteenApiPort,
} from '../api/smartCanteenApi';

const props = defineProps<{
  api: SmartCanteenApiPort;
  scope: CanteenScope;
}>();

const today = new Date().toISOString().slice(0, 10);
const date = ref(today);
const mealTime = ref('');
const menus = ref<DinerMenu[]>([]);
const orders = ref<MealOrder[]>([]);
const selectedMenuId = ref('');
const quantities = ref<Record<string, number>>({});
const loadingMenus = ref(false);
const loadingOrders = ref(false);
const submitting = ref(false);
const cancelling = ref('');
const error = ref('');
const notice = ref('');

const selectedMenu = computed(() => menus.value.find((menu) => menu.id === selectedMenuId.value) ?? menus.value[0]);
const selectedItems = computed(() => Object.entries(quantities.value)
  .filter(([, quantity]) => quantity > 0)
  .map(([dishId, quantity]) => ({ dishId, quantity })));
const selectedCount = computed(() => selectedItems.value.reduce((sum, item) => sum + item.quantity, 0));

function randomKey(prefix: string): string {
  const uuid = globalThis.crypto?.randomUUID?.();
  return `${prefix}-${uuid ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`}`;
}

function quantityFor(dishId: string): number {
  return quantities.value[dishId] ?? 0;
}

function changeQuantity(dish: DinerMenuItem, delta: number): void {
  const next = Math.max(0, Math.min(20, quantityFor(dish.dishId) + delta));
  quantities.value = { ...quantities.value, [dish.dishId]: next };
}

async function loadMenus(): Promise<void> {
  if (!props.api.listDinerMenus) return;
  loadingMenus.value = true;
  error.value = '';
  try {
    menus.value = await props.api.listDinerMenus(props.scope, date.value, mealTime.value || undefined);
    if (!menus.value.some((menu) => menu.id === selectedMenuId.value)) {
      selectedMenuId.value = menus.value[0]?.id ?? '';
    }
    quantities.value = {};
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '菜单加载失败';
  } finally {
    loadingMenus.value = false;
  }
}

async function loadOrders(): Promise<void> {
  if (!props.api.listMealOrders) return;
  loadingOrders.value = true;
  try {
    orders.value = await props.api.listMealOrders(props.scope);
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '订单加载失败';
  } finally {
    loadingOrders.value = false;
  }
}

async function refresh(): Promise<void> {
  notice.value = '';
  await Promise.all([loadMenus(), loadOrders()]);
}

async function submitOrder(): Promise<void> {
  if (!props.api.createMealOrder) return;
  if (!selectedMenu.value) {
    notice.value = '请先选择一个已发布菜单';
    return;
  }
  if (selectedItems.value.length === 0) {
    notice.value = '请先选择要订购的菜品';
    return;
  }
  submitting.value = true;
  error.value = '';
  notice.value = '';
  try {
    const order = await props.api.createMealOrder(
      { menuId: selectedMenu.value.id, items: selectedItems.value },
      randomKey('diner-order'),
      props.scope,
    );
    notice.value = `订单 ${order.orderNo} 已创建，当前状态为待支付。`;
    quantities.value = {};
    await loadOrders();
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '下单失败';
  } finally {
    submitting.value = false;
  }
}

async function cancelOrder(order: MealOrder): Promise<void> {
  if (!props.api.cancelMealOrder || order.status !== 'CREATED') return;
  cancelling.value = order.id;
  error.value = '';
  try {
    await props.api.cancelMealOrder(order.id, props.scope);
    notice.value = `订单 ${order.orderNo} 已取消。`;
    await loadOrders();
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '取消订单失败';
  } finally {
    cancelling.value = '';
  }
}

function statusLabel(status: string): string {
  return status === 'CREATED' ? '待支付' : status === 'CANCELLED' ? '已取消' : status;
}

function mealTimeLabel(value: string): string {
  return ({ BREAKFAST: '早餐', LUNCH: '午餐', DINNER: '晚餐', SNACK: '加餐' } as Record<string, string>)[value] ?? value;
}

watch([date, mealTime], loadMenus);
onMounted(refresh);
</script>

<template>
  <section class="diner-page">
    <header class="diner-hero">
      <div>
        <p class="eyebrow">EMPLOYEE / STUDENT SERVICE</p>
        <h1>今天吃什么？</h1>
        <p>查看已公示菜单，选择菜品创建个人消费订单。</p>
      </div>
      <div class="hero-note"><span>当前范围</span><strong>{{ scope.canteenId }}</strong><small>订单仅归属于当前登录用户</small></div>
    </header>

    <p v-if="error" class="feedback error">{{ error }}</p>
    <p v-if="notice" class="feedback success">{{ notice }}</p>

    <section class="filters panel">
      <label>用餐日期<input v-model="date" type="date" /></label>
      <label>餐次<select v-model="mealTime"><option value="">全部餐次</option><option value="BREAKFAST">早餐</option><option value="LUNCH">午餐</option><option value="DINNER">晚餐</option><option value="SNACK">加餐</option></select></label>
      <button class="secondary" type="button" :disabled="loadingMenus || loadingOrders" @click="refresh">{{ loadingMenus || loadingOrders ? '刷新中…' : '刷新' }}</button>
    </section>

    <div class="content-grid">
      <section class="panel menu-panel">
        <div class="section-heading"><div><p class="eyebrow dark">PUBLISHED MENU</p><h2>已公示菜单</h2></div><span>{{ menus.length }} 份菜单</span></div>
        <div v-if="loadingMenus" class="empty">正在读取菜单…</div>
        <div v-else-if="menus.length === 0" class="empty">这一天暂时没有已公示菜单。</div>
        <template v-else>
          <div class="menu-tabs">
            <button v-for="menu in menus" :key="menu.id" type="button" :class="{ active: menu.id === selectedMenu?.id }" @click="selectedMenuId = menu.id; quantities = {}">
              {{ mealTimeLabel(menu.mealTime) }}<small>{{ menu.items.length }} 道菜</small>
            </button>
          </div>
          <div class="dish-list">
            <article v-for="dish in selectedMenu?.items" :key="dish.dishId" class="dish-card">
              <div class="dish-avatar">{{ dish.name.slice(0, 1) }}</div>
              <div class="dish-info"><strong>{{ dish.name }}</strong><span>{{ dish.category || '今日菜品' }}</span><small>{{ dish.description || '暂无菜品说明' }}</small></div>
              <div class="quantity-control"><button type="button" aria-label="减少数量" :disabled="quantityFor(dish.dishId) === 0" @click="changeQuantity(dish, -1)">−</button><b>{{ quantityFor(dish.dishId) }}</b><button type="button" aria-label="增加数量" @click="changeQuantity(dish, 1)">＋</button></div>
            </article>
          </div>
          <footer class="order-bar"><span>已选 {{ selectedCount }} 份</span><button class="primary" type="button" :disabled="submitting || selectedCount === 0" @click="submitOrder">{{ submitting ? '提交中…' : '创建未支付订单' }}</button></footer>
        </template>
      </section>

      <section class="panel order-panel">
        <div class="section-heading"><div><p class="eyebrow dark">MY ORDERS</p><h2>我的订单</h2></div><span>{{ orders.length }} 笔</span></div>
        <div v-if="loadingOrders" class="empty">正在读取订单…</div>
        <div v-else-if="orders.length === 0" class="empty">还没有消费订单，先从左侧菜单开始吧。</div>
        <div v-else class="order-list">
          <article v-for="order in orders" :key="order.id" class="order-card">
            <div class="order-top"><div><strong>{{ order.orderNo }}</strong><span>{{ order.mealDate }} · {{ mealTimeLabel(order.mealTime) }}</span></div><em :class="order.status.toLowerCase()">{{ statusLabel(order.status) }}</em></div>
            <div class="order-items"><span v-for="item in order.items" :key="item.dishId">{{ item.dishName }} × {{ item.quantity }}</span></div>
            <div class="order-bottom"><span>金额待支付</span><button v-if="order.status === 'CREATED'" type="button" :disabled="cancelling === order.id" @click="cancelOrder(order)">{{ cancelling === order.id ? '取消中…' : '取消订单' }}</button></div>
          </article>
        </div>
      </section>
    </div>
  </section>
</template>

<style scoped>
.diner-page { display: grid; gap: 18px; }
.diner-hero { display: flex; justify-content: space-between; gap: 24px; padding: 30px 34px; border-radius: 18px; color: #fff; background: linear-gradient(120deg, #275e4a, #419066); box-shadow: 0 14px 35px rgba(40, 104, 75, .16); }
.eyebrow { margin: 0 0 7px; color: #f1d17a; font-size: 10px; font-weight: 800; letter-spacing: .16em; }.eyebrow.dark { color: #81a48f; }
.diner-hero h1 { margin: 0 0 9px; font-size: clamp(25px, 3vw, 35px); }.diner-hero p:last-child { margin: 0; color: rgba(255,255,255,.76); font-size: 13px; }
.hero-note { display: grid; align-content: center; min-width: 180px; padding-left: 25px; border-left: 1px solid rgba(255,255,255,.27); }.hero-note span, .hero-note small { color: rgba(255,255,255,.63); font-size: 11px; }.hero-note strong { margin: 5px 0; font-size: 17px; }.hero-note small { display: block; }
.panel { border: 1px solid #e5eae4; border-radius: 14px; background: #fff; box-shadow: 0 8px 22px rgba(45,64,50,.035); }.filters { display: flex; align-items: end; gap: 14px; padding: 15px 17px; }.filters label { display: grid; gap: 6px; color: #78897f; font-size: 11px; }.filters input, .filters select { min-width: 145px; padding: 8px 10px; border: 1px solid #dce5dc; border-radius: 8px; color: #385347; background: #fbfdfb; }.filters button { margin-left: auto; }
.feedback { margin: 0; padding: 11px 14px; border-radius: 9px; font-size: 12px; }.feedback.error { color: #9a493f; background: #fff0ec; }.feedback.success { color: #317454; background: #e9f7ed; }
.content-grid { display: grid; grid-template-columns: minmax(0, 1.35fr) minmax(320px, .85fr); gap: 18px; }.menu-panel, .order-panel { min-width: 0; padding: 20px; }.section-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 14px; }.section-heading h2 { margin: 0; color: #29483a; font-size: 19px; }.section-heading > span { color: #9baa9f; font-size: 11px; }.menu-tabs { display: flex; gap: 8px; margin-bottom: 12px; overflow-x: auto; }.menu-tabs button { display: grid; gap: 3px; flex: 0 0 auto; padding: 9px 13px; border: 1px solid #e3eae3; border-radius: 9px; color: #677c6f; background: #fff; text-align: left; cursor: pointer; }.menu-tabs button.active { border-color: #8cc19b; color: #216e4b; background: #e9f6eb; font-weight: 800; }.menu-tabs small { color: #9aac9f; font-size: 10px; font-weight: 400; }
.dish-list, .order-list { display: grid; gap: 9px; }.dish-card { display: flex; align-items: center; gap: 11px; padding: 11px; border: 1px solid #edf1ed; border-radius: 11px; }.dish-avatar { display: grid; flex: 0 0 auto; place-items: center; width: 39px; height: 39px; border-radius: 11px; color: #2c7652; background: #e3f2e6; font-weight: 800; }.dish-info { display: grid; gap: 3px; min-width: 0; }.dish-info strong { color: #365245; font-size: 13px; }.dish-info span, .dish-info small { overflow: hidden; color: #94a49a; font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }.quantity-control { display: flex; align-items: center; gap: 9px; margin-left: auto; }.quantity-control button { width: 25px; height: 25px; border: 1px solid #dce8dc; border-radius: 7px; color: #2a7852; background: #f3faf4; cursor: pointer; }.quantity-control button:disabled { color: #c7d1c9; background: #fafcfa; cursor: not-allowed; }.quantity-control b { min-width: 16px; color: #415a4d; text-align: center; font-size: 12px; }
.order-bar { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-top: 15px; padding-top: 15px; border-top: 1px solid #edf1ed; color: #789084; font-size: 12px; }.primary, .secondary { padding: 9px 14px; border-radius: 8px; cursor: pointer; }.primary { border: 1px solid #2f8260; color: #fff; background: #2f8260; }.primary:disabled { border-color: #adc8b3; background: #adc8b3; cursor: not-allowed; }.secondary { border: 1px solid #d7e2d8; color: #5d7568; background: #fff; }
.order-card { padding: 13px; border: 1px solid #edf1ed; border-radius: 11px; }.order-top, .order-bottom { display: flex; align-items: center; justify-content: space-between; gap: 8px; }.order-top strong, .order-top span { display: block; }.order-top strong { color: #385447; font-size: 13px; }.order-top span { margin-top: 3px; color: #9aa99f; font-size: 10px; }.order-top em { padding: 4px 7px; border-radius: 6px; color: #9a7331; background: #fff3d7; font-size: 10px; font-style: normal; }.order-top em.cancelled { color: #8f9891; background: #f0f3f0; }.order-items { display: flex; flex-wrap: wrap; gap: 5px; margin: 12px 0; }.order-items span { padding: 4px 6px; border-radius: 5px; color: #61786b; background: #f3f7f2; font-size: 10px; }.order-bottom { padding-top: 10px; border-top: 1px solid #f0f2ef; color: #9aa79f; font-size: 10px; }.order-bottom button { padding: 0; border: 0; color: #b35a50; background: transparent; font-size: 11px; cursor: pointer; }.order-bottom button:disabled { color: #c8b4b1; cursor: wait; }.empty { padding: 42px 15px; color: #9aa79f; text-align: center; font-size: 12px; }
@media (max-width: 960px) { .content-grid { grid-template-columns: 1fr; } } @media (max-width: 600px) { .diner-hero { display: grid; padding: 24px; }.hero-note { min-width: 0; padding: 13px 0 0; border-top: 1px solid rgba(255,255,255,.27); border-left: 0; }.filters { align-items: stretch; flex-wrap: wrap; }.filters label { flex: 1; }.filters button { margin-left: 0; } }
</style>
