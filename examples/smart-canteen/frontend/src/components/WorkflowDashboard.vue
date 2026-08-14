<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import type { LedgerAlert, ProcurementItem } from '../api/generated/client';
import type { CanteenScope, SmartCanteenApiPort } from '../api/smartCanteenApi';

const props = defineProps<{ api: SmartCanteenApiPort; scope?: CanteenScope }>();

const menuId = 'MENU-001';
const menuStatus = ref('DRAFT');
const procurementItems = ref<ProcurementItem[]>([]);
const ledgerAlert = ref<LedgerAlert | null>(null);
const loading = ref(true);
const submitting = ref(false);
const error = ref('');
const notice = ref('');

const canApprove = computed(() => menuStatus.value === 'PENDING_APPROVAL');
const canGenerate = computed(() => menuStatus.value === 'APPROVED');

function messageOf(reason: unknown): string {
  return reason instanceof Error ? reason.message : '请求失败';
}

async function perform(action: () => Promise<void>): Promise<void> {
  submitting.value = true;
  error.value = '';
  notice.value = '';
  try {
    await action();
  } catch (reason) {
    error.value = messageOf(reason);
  } finally {
    submitting.value = false;
  }
}

async function loadAlert(): Promise<void> {
  loading.value = true;
  error.value = '';
  try {
    ledgerAlert.value = props.scope
      ? await props.api.getCurrentLedgerAlert(props.scope)
      : await props.api.getCurrentLedgerAlert();
  } catch (reason) {
    error.value = messageOf(reason);
  } finally {
    loading.value = false;
  }
}

async function submitMenu(): Promise<void> {
  await perform(async () => {
    const menu = props.scope
      ? await props.api.submitMenu(menuId, props.scope)
      : await props.api.submitMenu(menuId);
    menuStatus.value = menu.status;
    notice.value = '菜单已进入审批队列';
  });
}

async function approveMenu(): Promise<void> {
  await perform(async () => {
    const menu = props.scope
      ? await props.api.decideMenuApproval(
          menuId,
          'APPROVE',
          '页面审批通过',
          props.scope,
        )
      : await props.api.decideMenuApproval(menuId, 'APPROVE', '页面审批通过');
    menuStatus.value = menu.status;
    notice.value = '菜单审批通过，可以生成采购缺口';
  });
}

async function generatePlan(): Promise<void> {
  await perform(async () => {
    const plan = props.scope
      ? await props.api.generateProcurementPlan(menuId, props.scope)
      : await props.api.generateProcurementPlan(menuId);
    procurementItems.value = plan.items;
    notice.value = plan.items.length ? '采购缺口已生成' : '当前库存充足';
  });
}

async function receiveFlour(): Promise<void> {
  await perform(async () => {
    if (props.scope) {
      await props.api.receiveInventory(
        `receipt-${menuId}-FLOUR`,
        'FLOUR',
        1.5,
        'kg',
        props.scope,
      );
    } else {
      await props.api.receiveInventory(`receipt-${menuId}-FLOUR`, 'FLOUR', 1.5, 'kg');
    }
    procurementItems.value = [];
    notice.value = '面粉已入库并换算为基础单位';
  });
}

async function completeLedger(): Promise<void> {
  await perform(async () => {
    ledgerAlert.value = props.scope
      ? await props.api.completeLedgerRecord('PURCHASE_ACCEPTANCE', props.scope)
      : await props.api.completeLedgerRecord('PURCHASE_ACCEPTANCE');
    notice.value = '采购验收台账已完成，预警已重新计算';
  });
}

onMounted(loadAlert);
</script>

<template>
  <main class="dashboard">
    <header class="hero">
      <p class="eyebrow">SMART CANTEEN · API WORKFLOW</p>
      <h1>智慧食堂业务闭环</h1>
      <p>用一条真实业务链验证接口契约、前端状态与后端规则的一致性。</p>
    </header>

    <p v-if="error" class="message error" data-testid="error">{{ error }}</p>
    <p v-if="notice" class="message notice">{{ notice }}</p>

    <section class="workflow-grid">
      <article class="card">
        <span class="step">01</span>
        <h2>菜单审批</h2>
        <p data-testid="menu-status">当前状态：<strong>{{ menuStatus }}</strong></p>
        <div class="actions">
          <button data-testid="submit-menu" :disabled="submitting || menuStatus !== 'DRAFT'" @click="submitMenu">
            提交审批
          </button>
          <button data-testid="approve-menu" :disabled="submitting || !canApprove" @click="approveMenu">
            审批通过
          </button>
        </div>
      </article>

      <article class="card">
        <span class="step">02</span>
        <h2>采购与入库</h2>
        <p v-if="!procurementItems.length" class="muted">尚未生成采购缺口</p>
        <ul v-else>
          <li v-for="item in procurementItems" :key="item.materialId">
            {{ item.materialId }}：缺口 {{ item.shortageBaseQuantity }} {{ item.baseUnit }}
          </li>
        </ul>
        <div class="actions">
          <button :disabled="submitting || !canGenerate" @click="generatePlan">生成采购计划</button>
          <button :disabled="submitting || !procurementItems.length" @click="receiveFlour">模拟入库 1.5 kg</button>
        </div>
      </article>

      <article class="card">
        <span class="step">03</span>
        <h2>台账预警</h2>
        <p v-if="loading" data-testid="loading">正在加载台账状态…</p>
        <p v-else-if="ledgerAlert?.cleared" data-testid="empty-alert" class="success">无待补台账</p>
        <template v-else>
          <p>待补项：{{ ledgerAlert?.missingLedgerCodes.join('、') }}</p>
          <button :disabled="submitting" @click="completeLedger">完成采购验收台账</button>
        </template>
      </article>
    </section>
  </main>
</template>

<style scoped>
:global(*) { box-sizing: border-box; }
:global(body) { margin: 0; color: #17231d; background: #eef3ee; font-family: Inter, "Microsoft YaHei", sans-serif; }
.dashboard { min-height: 100vh; padding: 64px clamp(24px, 6vw, 96px); }
.hero { max-width: 760px; margin-bottom: 36px; }
.eyebrow { color: #217a55; font-size: 12px; font-weight: 800; letter-spacing: .18em; }
h1 { margin: 8px 0 12px; font-size: clamp(36px, 6vw, 68px); line-height: 1; }
.hero > p:last-child { color: #5d6c64; font-size: 18px; }
.workflow-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(260px, 1fr)); gap: 20px; }
.card { min-height: 300px; padding: 28px; border: 1px solid #d6e0d7; border-radius: 20px; background: rgba(255,255,255,.82); box-shadow: 0 16px 48px rgba(30,69,47,.08); }
.step { color: #217a55; font: 800 13px/1 monospace; }
h2 { margin: 16px 0 28px; font-size: 24px; }
.actions { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 28px; }
button { padding: 11px 16px; border: 0; border-radius: 10px; color: white; background: #217a55; font-weight: 700; cursor: pointer; }
button:disabled { cursor: not-allowed; opacity: .4; }
.message { padding: 14px 18px; border-radius: 12px; }
.error { color: #8c2525; background: #feecec; }
.notice, .success { color: #17623f; background: #e1f4e8; }
.muted { color: #78847d; }
ul { padding-left: 20px; }
</style>
