<script setup lang="ts">
import { onMounted, ref } from 'vue';
import type { DashboardSummary, RiskAssessment } from '../api/generated/client';
import type { CanteenScope, SmartCanteenApiPort } from '../api/smartCanteenApi';

const props = defineProps<{
  api: SmartCanteenApiPort;
  scope: CanteenScope;
}>();

const summary = ref<DashboardSummary | null>(null);
const risk = ref<RiskAssessment | null>(null);
const loading = ref(true);
const error = ref('');

function messageOf(reason: unknown): string {
  return reason instanceof Error ? reason.message : '运营概览加载失败';
}

async function load(): Promise<void> {
  loading.value = true;
  error.value = '';
  try {
    if (!props.api.getDashboardSummary) {
      return;
    }
    summary.value = await props.api.getDashboardSummary(props.scope);
    if (props.api.getDashboardRisk) {
      risk.value = await props.api.getDashboardRisk(props.scope);
    }
  } catch (reason) {
    error.value = messageOf(reason);
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<template>
  <section class="overview" data-testid="operations-overview">
    <div class="section-heading">
      <div>
        <p class="eyebrow">LIVE OPERATIONS</p>
        <h2>食堂运营概览</h2>
      </div>
      <span v-if="summary" class="scope">{{ scope.schoolId }} · {{ scope.canteenId }}</span>
    </div>

    <p v-if="loading" class="state" data-testid="overview-loading">正在加载运营指标…</p>
    <p v-else-if="error" class="state error" data-testid="overview-error">{{ error }}</p>
    <p v-else-if="!summary" class="state" data-testid="overview-empty">暂无运营概览</p>
    <div v-else class="metric-grid" data-testid="overview-summary">
      <article class="metric">
        <span>今日菜单</span>
        <strong>{{ summary.todayMenuCount }}</strong>
        <small>已发布 {{ summary.publishedMenuCount }} 份</small>
      </article>
      <article class="metric">
        <span>待处理采购</span>
        <strong>{{ summary.pendingPurchaseOrderCount }}</strong>
        <small>采购额 ¥{{ summary.purchaseAmount.toFixed(2) }}</small>
      </article>
      <article class="metric" :class="{ warning: summary.inventoryWarningCount > 0 }">
        <span>库存预警</span>
        <strong>{{ summary.inventoryWarningCount }}</strong>
        <small>批次与库存实时汇总</small>
      </article>
      <article class="metric" :class="{ warning: summary.openLedgerAlertCount > 0 }">
        <span>台账待补</span>
        <strong>{{ summary.openLedgerAlertCount }}</strong>
        <small>外部预警 {{ summary.openExternalAlertCount }} 条</small>
      </article>
      <article v-if="risk" class="risk" data-testid="overview-risk">
        <span>综合风险</span>
        <strong>{{ risk.level }} · {{ risk.score }}</strong>
        <small>{{ risk.factors.length ? risk.factors.join('；') : '当前没有额外风险因素' }}</small>
      </article>
    </div>
  </section>
</template>

<style scoped>
.overview { margin: 0 0 24px; padding: 24px 28px; border: 1px solid #d6e0d7; border-radius: 20px; background: rgba(255,255,255,.82); box-shadow: 0 16px 48px rgba(30,69,47,.08); }
.section-heading { display: flex; align-items: end; justify-content: space-between; gap: 16px; margin-bottom: 18px; }
.eyebrow { margin: 0 0 6px; color: #217a55; font-size: 11px; font-weight: 800; letter-spacing: .18em; }
h2 { margin: 0; font-size: 25px; }
.scope { color: #78847d; font-size: 12px; }
.metric-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 12px; }
.metric, .risk { display: grid; gap: 5px; padding: 16px; border-radius: 14px; background: #f1f6f1; }
.metric span, .risk span { color: #5d6c64; font-size: 13px; }
.metric strong, .risk strong { color: #17231d; font-size: 28px; }
.metric small, .risk small { color: #78847d; line-height: 1.4; }
.metric.warning { background: #fff4e4; }
.risk { background: #e6f3eb; }
.state { margin: 0; color: #78847d; }
.state.error { color: #8c2525; }
</style>
