<script setup lang="ts">
import axios from 'axios';
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import AgentMetricsDashboard from './components/AgentMetricsDashboard.vue';
import AssistantWorkspace from './components/AssistantWorkspace.vue';
import CanteenWorkspace from './components/CanteenWorkspace.vue';
import LoginPanel from './components/LoginPanel.vue';
import OperationsOverview from './components/OperationsOverview.vue';
import type { AuthSession, CanteenScope } from './api/smartCanteenApi';
import { SmartCanteenApi } from './api/smartCanteenApi';
import { routeFromLocation, type AppRouteId, writeRoute } from './router';

const api = new SmartCanteenApi(axios.create({ timeout: 10_000 }));
const session = ref<AuthSession | null>(api.getSession());
const authenticated = computed(() => session.value !== null);
const activeView = ref<AppRouteId>(routeFromLocation());
const sidebarOpen = ref(false);
const scope = ref<CanteenScope>({
  schoolId: import.meta.env.VITE_SCHOOL_ID ?? 'SCHOOL-001',
  canteenId: import.meta.env.VITE_CANTEEN_ID ?? 'CANTEEN-001',
});

type NavigationItem = { id: AppRouteId; label: string; icon: string };
type NavigationGroup = { label: string; items: NavigationItem[] };
const navigation: NavigationGroup[] = [
  { label: '工作台', items: [{ id: 'home', label: '首页', icon: '⌂' }] },
  { label: '食谱管理', items: [
    { id: 'ingredients', label: '食材品名', icon: '蔬' },
    { id: 'units', label: '基础单位', icon: '量' },
    { id: 'dishes', label: '菜品管理', icon: '菜' },
    { id: 'recipes', label: '食谱编辑', icon: '谱' },
    { id: 'menus', label: '食谱工作台', icon: '日' },
    { id: 'menu-approval', label: '待审批食谱', icon: '审' },
    { id: 'menu-published', label: '已公示食谱', icon: '公' },
  ] },
  { label: '采购管理', items: [
    { id: 'plans', label: '采购计划', icon: '计' },
    { id: 'orders', label: '采购订单', icon: '单' },
    { id: 'receiving', label: '验收入库', icon: '验' },
    { id: 'suppliers', label: '供应商', icon: '供' },
  ] },
  { label: '库存管理', items: [
    { id: 'inventory', label: '库存批次', icon: '存' },
    { id: 'stockout', label: '领用出库', icon: '出' },
  ] },
  { label: '安全管理', items: [
    { id: 'ledger', label: '台账管理', icon: '账' },
    { id: 'alerts', label: '预警中心', icon: '警' },
    { id: 'trace', label: '溯源管理', icon: '溯' },
  ] },
  { label: '智能能力', items: [{ id: 'assistant', label: '智能助手', icon: 'AI' }] },
];
const allNavigationItems = computed(() => navigation.flatMap((group) => group.items));
const roles = computed(() => {
  const info = session.value?.userInfo;
  return info?.roles ?? (info?.role ? [info.role] : []);
});
const isAdmin = computed(() => roles.value.some((role) => ['SYSTEM_ADMIN', 'SCHOOL_ADMIN'].includes(role)));
const userLabel = computed(() => session.value?.userInfo.nickname || session.value?.userInfo.username || '当前用户');
const activeLabel = computed(() => allNavigationItems.value.find((item) => item.id === activeView.value)?.label ?? '首页');

function signedIn(next: AuthSession): void {
  session.value = next;
  navigate('home');
}

async function signOut(): Promise<void> {
  try {
    await api.logout(session.value?.refreshToken);
  } finally {
    session.value = null;
  }
}
function navigate(id: AppRouteId): void {
  activeView.value = id;
  writeRoute(id);
  sidebarOpen.value = false;
}

function syncRoute(): void {
  activeView.value = routeFromLocation();
}

onMounted(() => window.addEventListener('hashchange', syncRoute));
onBeforeUnmount(() => window.removeEventListener('hashchange', syncRoute));
</script>

<template>
  <LoginPanel v-if="!authenticated" :api="api" @authenticated="signedIn" />
  <div v-else class="app-shell">
    <aside class="sidebar" :class="{ open: sidebarOpen }">
      <div class="brand"><div class="brand-mark">食</div><div><strong>智慧食堂</strong><span>单食堂运营中台</span></div></div>
      <nav class="navigation">
        <section v-for="group in navigation" :key="group.label" class="nav-group">
          <p>{{ group.label }}</p>
          <button v-for="item in group.items" :key="item.id" type="button" class="nav-item" :class="{ active: activeView === item.id }" @click="navigate(item.id)"><span class="nav-icon">{{ item.icon }}</span><span>{{ item.label }}</span></button>
        </section>
      </nav>
      <div class="sidebar-footer"><span class="online-dot"></span>业务服务正常</div>
    </aside>
    <main class="main-area">
      <header class="topbar"><button class="mobile-menu" type="button" @click="sidebarOpen = !sidebarOpen">☰</button><div class="breadcrumbs"><span>智慧食堂</span><b>/</b><strong>{{ activeLabel }}</strong></div><div class="account-area"><div class="canteen-badge"><span class="online-dot"></span>单食堂</div><div class="account-name"><span>{{ userLabel }}</span><small>{{ roles.join(' / ') }}</small></div><button class="logout" type="button" @click="signOut">退出</button></div></header>
      <section class="content-area">
        <template v-if="activeView === 'home'">
          <section class="welcome-panel"><div><p class="eyebrow">SMART CANTEEN OPERATIONS</p><h1>今天的食堂运营，一目了然</h1><p>围绕食谱、采购、库存与安全台账建立完整业务闭环。</p></div><div class="welcome-date"><span>当前日期</span><strong>{{ new Date().toLocaleDateString('zh-CN') }}</strong></div></section>
          <OperationsOverview :api="api" :scope="scope" />
          <section class="quick-grid"><button class="quick-card" type="button" @click="navigate('menus')"><span class="quick-icon peach">谱</span><span><strong>编辑今日食谱</strong><small>配置菜品与配方，提交审批</small></span><b>→</b></button><button class="quick-card" type="button" @click="navigate('plans')"><span class="quick-icon mint">计</span><span><strong>生成采购计划</strong><small>根据公示食谱自动计算缺口</small></span><b>→</b></button><button class="quick-card" type="button" @click="navigate('receiving')"><span class="quick-icon blue">验</span><span><strong>处理验收入库</strong><small>登记批次、有效期和溯源码</small></span><b>→</b></button><button class="quick-card" type="button" @click="navigate('alerts')"><span class="quick-icon yellow">警</span><span><strong>查看预警中心</strong><small>跟进台账缺项与安全预警</small></span><b>→</b></button></section>
          <AgentMetricsDashboard v-if="isAdmin" :api="api" :scope="scope" />
        </template>
        <AssistantWorkspace v-else-if="activeView === 'assistant'" :api="api" :scope="scope" :actor-id="session?.userInfo.userId" />
        <CanteenWorkspace v-else :api="api" :scope="scope" :view="activeView" :roles="roles" />
      </section>
    </main>
  </div>
</template>

<style scoped>
:global(*) { box-sizing: border-box; }
:global(body) { margin: 0; color: #263d34; background: #f5f7f3; font-family: Inter, "PingFang SC", "Microsoft YaHei", sans-serif; }
:global(button), :global(input), :global(select), :global(textarea) { font: inherit; }
.app-shell { display: flex; min-height: 100vh; }
.sidebar { position: fixed; z-index: 10; display: flex; flex-direction: column; width: 238px; height: 100vh; padding: 26px 14px 18px; border-right: 1px solid #e5e9e3; background: #fff; }
.brand { display: flex; align-items: center; gap: 11px; padding: 0 10px 28px; }
.brand-mark { display: grid; place-items: center; width: 36px; height: 36px; border-radius: 11px; color: #fff; background: #2f8260; font-weight: 800; }
.brand strong, .brand span { display: block; }.brand strong { color: #1e352c; font-size: 17px; }.brand span { margin-top: 3px; color: #9aa69e; font-size: 11px; }
.navigation { flex: 1; overflow-y: auto; }.nav-group { margin-bottom: 17px; }.nav-group p { margin: 0 10px 7px; color: #a2ada6; font-size: 11px; font-weight: 800; letter-spacing: .1em; }
.nav-item { display: flex; align-items: center; gap: 10px; width: 100%; margin: 2px 0; padding: 10px 11px; border: 0; border-radius: 9px; color: #5f7167; background: transparent; text-align: left; font-size: 13px; cursor: pointer; }.nav-item:hover { color: #277552; background: #f0f7f1; }.nav-item.active { color: #216e4b; background: #e8f4ea; font-weight: 800; }
.nav-icon { display: grid; place-items: center; width: 24px; height: 24px; border-radius: 7px; color: #6b8175; background: #f1f4ef; font-size: 11px; font-weight: 800; }.nav-item.active .nav-icon { color: #267452; background: #cfead6; }
.sidebar-footer { display: flex; align-items: center; gap: 7px; padding: 13px 10px 0; border-top: 1px solid #edf0eb; color: #9aa69e; font-size: 11px; }.online-dot { display: inline-block; width: 7px; height: 7px; border-radius: 50%; background: #54b77d; box-shadow: 0 0 0 3px #e2f5e8; }
.main-area { flex: 1; min-width: 0; margin-left: 238px; }.topbar { display: flex; align-items: center; justify-content: space-between; min-height: 72px; padding: 0 38px; border-bottom: 1px solid #e8ece6; background: rgba(255, 255, 255, .88); }.breadcrumbs { display: flex; align-items: center; gap: 9px; color: #a1aca5; font-size: 13px; }.breadcrumbs b { color: #d1d8d1; font-weight: 400; }.breadcrumbs strong { color: #3d5449; }
.account-area { display: flex; align-items: center; gap: 16px; }.canteen-badge { display: flex; align-items: center; gap: 8px; padding: 7px 10px; border-radius: 8px; color: #4e6859; background: #f0f7f0; font-size: 12px; font-weight: 700; }.account-name { display: grid; gap: 2px; color: #3d5449; font-size: 13px; text-align: right; }.account-name small { color: #9aa69e; font-size: 10px; }.logout { padding: 7px 11px; border: 1px solid #d9e1d9; border-radius: 7px; color: #64766b; background: #fff; cursor: pointer; }.mobile-menu { display: none; border: 0; color: #2f8260; background: transparent; font-size: 20px; }
.content-area { width: min(1480px, 100%); margin: 0 auto; padding: 34px 40px 60px; }.welcome-panel { display: flex; justify-content: space-between; gap: 30px; margin-bottom: 24px; padding: 30px 34px; border-radius: 18px; color: #fff; background: linear-gradient(118deg, #225b47 0%, #338663 58%, #77ad72 100%); box-shadow: 0 14px 35px rgba(40, 104, 75, .18); }.eyebrow { margin: 0 0 7px; color: #e8c96e; font-size: 10px; font-weight: 800; letter-spacing: .17em; }.welcome-panel h1 { margin: 0 0 9px; font-size: clamp(24px, 3vw, 34px); }.welcome-panel p { margin: 0; color: rgba(255,255,255,.77); font-size: 13px; }.welcome-date { display: grid; align-content: center; min-width: 160px; padding-left: 30px; border-left: 1px solid rgba(255,255,255,.24); }.welcome-date span { color: rgba(255,255,255,.65); font-size: 11px; }.welcome-date strong { margin-top: 6px; font-size: 16px; }
.quick-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 13px; margin: 22px 0 28px; }.quick-card { display: flex; align-items: center; gap: 11px; min-width: 0; padding: 16px; border: 1px solid #e5eae4; border-radius: 13px; color: #355043; background: #fff; text-align: left; box-shadow: 0 8px 20px rgba(45, 64, 50, .04); cursor: pointer; }.quick-card:hover { border-color: #a8c9af; }.quick-icon { display: grid; flex: 0 0 auto; place-items: center; width: 34px; height: 34px; border-radius: 10px; font-weight: 800; }.quick-icon.peach { color: #9b5f3f; background: #fae9dc; }.quick-icon.mint { color: #317453; background: #e0f2e6; }.quick-icon.blue { color: #39729a; background: #e0f0f7; }.quick-icon.yellow { color: #95702c; background: #fff2cd; }.quick-card span:nth-child(2) { display: grid; gap: 4px; min-width: 0; }.quick-card strong, .quick-card small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.quick-card strong { color: #345044; font-size: 13px; }.quick-card small { color: #97a49b; font-size: 10px; }.quick-card b { margin-left: auto; color: #aab7ad; font-size: 18px; font-weight: 400; }
@media (max-width: 1100px) { .quick-grid { grid-template-columns: repeat(2, 1fr); } } @media (max-width: 760px) { .sidebar { transform: translateX(-100%); transition: transform .2s ease; }.sidebar.open { transform: translateX(0); box-shadow: 12px 0 30px rgba(30, 60, 40, .15); }.main-area { margin-left: 0; }.topbar { padding: 0 18px; }.mobile-menu { display: block; }.breadcrumbs { display: none; }.account-name { display: none; }.content-area { padding: 22px 16px 40px; }.welcome-panel { display: grid; padding: 24px; }.welcome-date { min-width: 0; padding: 14px 0 0; border-top: 1px solid rgba(255,255,255,.24); border-left: 0; } } @media (max-width: 520px) { .quick-grid { grid-template-columns: 1fr; }.canteen-badge { display: none; } }
</style>
