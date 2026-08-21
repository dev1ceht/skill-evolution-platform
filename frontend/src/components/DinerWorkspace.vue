<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import type {
  CanteenScope,
  DinerMenu,
  DinerMenuItem,
  MealOrder,
  SmartCanteenApiPort,
} from '../api/smartCanteenApi';
import type {
  DinerComplaint,
  DinerComplaintRequest,
  EmployeeMealReviewRequest,
  MealReview,
} from '../api/generated/client';

const props = defineProps<{
  api: SmartCanteenApiPort;
  scope: CanteenScope;
}>();

const today = new Date().toISOString().slice(0, 10);
const date = ref(today);
const mealTime = ref('');
const menus = ref<DinerMenu[]>([]);
const orders = ref<MealOrder[]>([]);
const reviews = ref<MealReview[]>([]);
const complaints = ref<DinerComplaint[]>([]);
const selectedMenuId = ref('');
const quantities = ref<Record<string, number>>({});
const loadingMenus = ref(false);
const loadingOrders = ref(false);
const submitting = ref(false);
const cancelling = ref('');
const reviewing = ref('');
const submittingComplaint = ref(false);
const reviewDrafts = ref<Record<string, { rating: number; content: string }>>({});
const complaintDraft = ref<DinerComplaintRequest>({
  category: 'SERVICE',
  subject: '',
  description: '',
  relatedOrderId: undefined,
});
const error = ref('');
const notice = ref('');

const selectedMenu = computed(() => menus.value.find((menu) => menu.id === selectedMenuId.value) ?? menus.value[0]);
const selectedItems = computed(() => Object.entries(quantities.value)
  .filter(([, quantity]) => quantity > 0)
  .map(([dishId, quantity]) => ({ dishId, quantity })));
const selectedCount = computed(() => selectedItems.value.reduce((sum, item) => sum + item.quantity, 0));

function hasReview(orderId: string): boolean {
  return reviews.value.some((review) => review.orderId === orderId);
}

function reviewDraft(orderId: string): { rating: number; content: string } {
  return reviewDrafts.value[orderId] ?? { rating: 5, content: '' };
}

function setReviewRating(orderId: string, value: string): void {
  reviewDrafts.value = {
    ...reviewDrafts.value,
    [orderId]: { ...reviewDraft(orderId), rating: Number(value) },
  };
}

function setReviewContent(orderId: string, value: string): void {
  reviewDrafts.value = {
    ...reviewDrafts.value,
    [orderId]: { ...reviewDraft(orderId), content: value },
  };
}

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

async function loadFeedback(): Promise<void> {
  const loaders: Promise<void>[] = [];
  if (props.api.listMealReviews) {
    loaders.push(props.api.listMealReviews(props.scope).then((records) => {
      reviews.value = records;
    }));
  }
  if (props.api.listDinerComplaints) {
    loaders.push(props.api.listDinerComplaints(props.scope).then((records) => {
      complaints.value = records;
    }));
  }
  if (loaders.length === 0) return;
  try {
    await Promise.all(loaders);
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '评价或投诉加载失败';
  }
}

async function refresh(): Promise<void> {
  notice.value = '';
  await Promise.all([loadMenus(), loadOrders(), loadFeedback()]);
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

async function submitReview(order: MealOrder): Promise<void> {
  if (!props.api.createMealReview || order.status === 'CANCELLED' || hasReview(order.id)) return;
  const draft = reviewDraft(order.id);
  reviewing.value = order.id;
  error.value = '';
  try {
    await props.api.createMealReview(
      { orderId: order.id, rating: draft.rating, content: draft.content.trim() || undefined },
      randomKey('diner-review'),
      props.scope,
    );
    notice.value = `订单 ${order.orderNo} 的评价已提交。`;
    await loadFeedback();
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '评价提交失败';
  } finally {
    reviewing.value = '';
  }
}

async function submitComplaint(): Promise<void> {
  if (!props.api.createDinerComplaint) return;
  const draft = complaintDraft.value;
  if (!draft.subject?.trim() || !draft.description?.trim()) {
    notice.value = '请填写投诉主题和问题描述。';
    return;
  }
  submittingComplaint.value = true;
  error.value = '';
  try {
    await props.api.createDinerComplaint(
      {
        category: draft.category,
        subject: draft.subject.trim(),
        description: draft.description.trim(),
        relatedOrderId: draft.relatedOrderId || undefined,
      },
      randomKey('diner-complaint'),
      props.scope,
    );
    notice.value = '投诉已提交，当前状态为已提交。';
    complaintDraft.value = { category: 'SERVICE', subject: '', description: '', relatedOrderId: undefined };
    await loadFeedback();
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : '投诉提交失败';
  } finally {
    submittingComplaint.value = false;
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

    <section v-if="api.listMealReviews || api.createMealReview || api.listDinerComplaints || api.createDinerComplaint" class="feedback-grid">
      <section class="panel feedback-panel">
        <div class="section-heading"><div><p class="eyebrow dark">MY FEEDBACK</p><h2>我的评价</h2></div><span>{{ reviews.length }} 条</span></div>
        <div v-if="reviews.length > 0" class="feedback-list">
          <article v-for="review in reviews" :key="review.id" class="feedback-card">
            <div class="feedback-card-top"><strong>{{ review.orderNo }}</strong><span>{{ '★'.repeat(review.rating) }}{{ '☆'.repeat(5 - review.rating) }}</span></div>
            <p>{{ review.content || '未填写文字评价' }}</p>
            <small>{{ review.createdAt.slice(0, 10) }} · 已提交</small>
          </article>
        </div>
        <div v-else class="empty compact">还没有已提交的评价。</div>
        <div v-if="orders.some((order) => order.status !== 'CANCELLED' && !hasReview(order.id))" class="review-forms">
          <h3>评价我的订单</h3>
          <article v-for="order in orders.filter((item) => item.status !== 'CANCELLED' && !hasReview(item.id))" :key="order.id" class="review-form">
            <div class="feedback-card-top"><strong>{{ order.orderNo }}</strong><span>可评价</span></div>
            <label>评分
              <select :value="reviewDraft(order.id).rating" @change="setReviewRating(order.id, ($event.target as HTMLSelectElement).value)">
                <option :value="5">5 星</option><option :value="4">4 星</option><option :value="3">3 星</option><option :value="2">2 星</option><option :value="1">1 星</option>
              </select>
            </label>
            <textarea :value="reviewDraft(order.id).content" maxlength="2000" placeholder="说说这次用餐体验（选填）" @input="setReviewContent(order.id, ($event.target as HTMLTextAreaElement).value)" />
            <button class="primary small-button" type="button" aria-label="提交评价" :disabled="reviewing === order.id" @click="submitReview(order)">{{ reviewing === order.id ? '提交中…' : '提交评价' }}</button>
          </article>
        </div>
      </section>

      <section class="panel feedback-panel">
        <div class="section-heading"><div><p class="eyebrow dark">SERVICE DESK</p><h2>投诉建议</h2></div><span>{{ complaints.length }} 条</span></div>
        <form v-if="api.createDinerComplaint" class="complaint-form" @submit.prevent="submitComplaint">
          <label>分类<select v-model="complaintDraft.category"><option value="SERVICE">服务</option><option value="FOOD_QUALITY">菜品质量</option><option value="HYGIENE">卫生</option><option value="QUEUE">排队</option><option value="PAYMENT">支付</option><option value="OTHER">其他</option></select></label>
          <label>主题<input v-model="complaintDraft.subject" maxlength="120" placeholder="例如：窗口服务" /></label>
          <label>关联订单（选填）<select v-model="complaintDraft.relatedOrderId"><option :value="undefined">不关联订单</option><option v-for="order in orders" :key="order.id" :value="order.id">{{ order.orderNo }}</option></select></label>
          <label>问题描述<textarea v-model="complaintDraft.description" maxlength="2000" placeholder="请描述遇到的问题" /></label>
          <button class="primary small-button" type="submit" data-testid="submit-complaint" :disabled="submittingComplaint">{{ submittingComplaint ? '提交中…' : '提交投诉' }}</button>
        </form>
        <div v-if="complaints.length > 0" class="feedback-list complaint-list">
          <article v-for="complaint in complaints" :key="complaint.id" class="feedback-card">
            <div class="feedback-card-top"><strong>{{ complaint.subject }}</strong><span>{{ complaint.category }}</span></div>
            <p>{{ complaint.description }}</p>
            <small>{{ complaint.createdAt.slice(0, 10) }} · {{ complaint.status === 'SUBMITTED' ? '已提交' : complaint.status }}</small>
          </article>
        </div>
        <div v-else class="empty compact">还没有投诉记录。</div>
      </section>
    </section>
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
.feedback-grid { display: grid; grid-template-columns: minmax(0, 1fr) minmax(320px, 1fr); gap: 18px; }.feedback-panel { min-width: 0; padding: 20px; }.feedback-list, .review-forms { display: grid; gap: 9px; }.feedback-card, .review-form { padding: 12px; border: 1px solid #edf1ed; border-radius: 10px; }.feedback-card-top { display: flex; align-items: center; justify-content: space-between; gap: 10px; }.feedback-card-top strong { color: #385447; font-size: 12px; }.feedback-card-top span { color: #d29539; font-size: 11px; }.feedback-card p { margin: 9px 0; color: #61786b; font-size: 11px; line-height: 1.55; }.feedback-card small { color: #9aa79f; font-size: 10px; }.review-forms { margin-top: 14px; padding-top: 14px; border-top: 1px solid #edf1ed; }.review-forms h3 { margin: 0; color: #5c7668; font-size: 12px; }.review-form { display: grid; gap: 8px; }.review-form label, .complaint-form label { display: grid; gap: 5px; color: #78897f; font-size: 10px; }.review-form select, .review-form textarea, .complaint-form input, .complaint-form select, .complaint-form textarea { box-sizing: border-box; width: 100%; padding: 8px 9px; border: 1px solid #dce5dc; border-radius: 8px; color: #385347; background: #fbfdfb; font: inherit; font-size: 11px; }.review-form textarea, .complaint-form textarea { min-height: 62px; resize: vertical; }.small-button { justify-self: start; padding: 7px 11px; font-size: 11px; }.complaint-form { display: grid; gap: 9px; margin-bottom: 14px; }.complaint-list { padding-top: 14px; border-top: 1px solid #edf1ed; }.empty.compact { padding: 20px 12px; }
@media (max-width: 960px) { .content-grid, .feedback-grid { grid-template-columns: 1fr; } } @media (max-width: 600px) { .diner-hero { display: grid; padding: 24px; }.hero-note { min-width: 0; padding: 13px 0 0; border-top: 1px solid rgba(255,255,255,.27); border-left: 0; }.filters { align-items: stretch; flex-wrap: wrap; }.filters label { flex: 1; }.filters button { margin-left: 0; } }
</style>
