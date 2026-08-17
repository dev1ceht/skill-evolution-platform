<script setup lang="ts">
import { onMounted, ref, watch } from 'vue';
import type { AgentMetrics } from '../api/generated/client';
import type { CanteenScope, SmartCanteenApiPort } from '../api/smartCanteenApi';

const props = defineProps<{
  api: SmartCanteenApiPort;
  scope: CanteenScope;
}>();

const metrics = ref<AgentMetrics | null>(null);
const loading = ref(false);
const error = ref('');
let requestSequence = 0;

function messageOf(reason: unknown): string {
  return reason instanceof Error ? reason.message : 'Agent 运行指标加载失败';
}

function windowBounds(): { from: string; to: string } {
  const to = new Date();
  const from = new Date(to.getTime() - 24 * 60 * 60 * 1000);
  return { from: from.toISOString(), to: to.toISOString() };
}

function percentage(value: number): string {
  return `${(value * 100).toFixed(1)}%`;
}

function duration(value: number): string {
  if (value < 1000) return `${value} ms`;
  return `${(value / 1000).toFixed(1)} s`;
}

async function load(): Promise<void> {
  if (!props.api.getAgentMetrics) {
    error.value = '当前客户端未接入 Agent 指标能力';
    return;
  }
  const sequence = ++requestSequence;
  loading.value = true;
  error.value = '';
  try {
    const bounds = windowBounds();
    const next = await props.api.getAgentMetrics(props.scope, bounds.from, bounds.to);
    if (sequence === requestSequence) metrics.value = next;
  } catch (reason) {
    if (sequence === requestSequence) error.value = messageOf(reason);
  } finally {
    if (sequence === requestSequence) loading.value = false;
  }
}

watch(
  () => `${props.scope.schoolId}/${props.scope.canteenId}`,
  () => { void load(); },
);

onMounted(() => { void load(); });
</script>

<template>
  <section class="agent-metrics" data-testid="agent-metrics">
    <div class="section-heading">
      <div>
        <p class="eyebrow">AGENT RUNTIME · OBSERVABILITY</p>
        <h2>运行指标</h2>
      </div>
      <div class="heading-actions">
        <span class="scope">{{ scope.schoolId }} · {{ scope.canteenId }}</span>
        <button type="button" data-testid="agent-metrics-refresh" :disabled="loading" @click="load">
          {{ loading ? '刷新中…' : '刷新' }}
        </button>
      </div>
    </div>

    <p v-if="loading" class="state" data-testid="agent-metrics-loading">正在读取最近 24 小时指标…</p>
    <p v-else-if="error" class="state error" data-testid="agent-metrics-error">{{ error }}</p>
    <p v-else-if="!metrics || metrics.totalRuns === 0" class="state" data-testid="agent-metrics-empty">
      最近 24 小时暂无 Agent Run 记录
    </p>
    <div v-else class="metrics-content" data-testid="agent-metrics-content">
      <div class="metric-grid">
        <article class="metric-card accent">
          <span>运行总数</span>
          <strong data-testid="agent-metrics-total">{{ metrics.totalRuns }}</strong>
          <small>成功率 {{ percentage(metrics.successRate) }}</small>
        </article>
        <article class="metric-card">
          <span>确认等待</span>
          <strong>{{ metrics.waitingConfirmationRuns }}</strong>
          <small>平均 {{ duration(metrics.averageConfirmationWaitMs) }}</small>
        </article>
        <article class="metric-card">
          <span>工具执行</span>
          <strong>{{ metrics.toolExecutions }}</strong>
          <small>失败 {{ metrics.toolFailures }} 次</small>
        </article>
        <article class="metric-card">
          <span>对账待处理</span>
          <strong>{{ metrics.reconciliationRequiredRuns }}</strong>
          <small>超时 {{ metrics.timedOutRuns }} 次</small>
        </article>
      </div>
      <dl class="detail-grid">
        <div><dt>平均 Run 耗时</dt><dd>{{ duration(metrics.averageRunDurationMs) }}</dd></div>
        <div><dt>平均工具耗时</dt><dd>{{ duration(metrics.averageToolDurationMs) }}</dd></div>
        <div><dt>幂等重放</dt><dd>{{ metrics.idempotencyReplayCount }}</dd></div>
        <div><dt>越权拒绝</dt><dd>{{ metrics.authorizationDeniedCount }}</dd></div>
      </dl>
      <p class="notice">指标按当前食堂范围聚合；越权拒绝仅统计该范围内的持久化审计记录。</p>
    </div>
  </section>
</template>

<style scoped>
.agent-metrics { margin: 0 0 24px; padding: 24px 28px; border: 1px solid #d6e0d7; border-radius: 20px; background: rgba(255,255,255,.86); box-shadow: 0 16px 48px rgba(30,69,47,.08); }
.section-heading { display: flex; align-items: end; justify-content: space-between; gap: 16px; margin-bottom: 18px; }
.eyebrow { margin: 0 0 6px; color: #217a55; font-size: 11px; font-weight: 800; letter-spacing: .18em; }
h2 { margin: 0; font-size: 25px; }
.heading-actions { display: flex; align-items: center; gap: 12px; }
.scope { color: #78847d; font-size: 12px; }
button { padding: 8px 12px; border: 1px solid #c7d6ca; border-radius: 9px; color: #217a55; background: white; font-weight: 700; cursor: pointer; }
button:disabled { cursor: wait; opacity: .65; }
.state { margin: 0; color: #78847d; }
.state.error { color: #8c2525; }
.metrics-content { display: grid; gap: 14px; }
.metric-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; }
.metric-card { display: grid; gap: 6px; padding: 14px; border: 1px solid #d6e0d7; border-radius: 13px; background: #f7fbf8; }
.metric-card.accent { border-color: #9bc7ad; background: #e6f3eb; }
.metric-card span, dt { color: #5d6c64; font-size: 12px; font-weight: 700; }
.metric-card strong { color: #17231d; font-size: 27px; line-height: 1; }
.metric-card small { color: #78847d; }
.detail-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; margin: 0; }
.detail-grid div { padding: 11px 13px; border-radius: 11px; background: #f4f7f4; }
dd { margin: 5px 0 0; color: #17231d; font-weight: 800; }
.notice { margin: 0; color: #78847d; font-size: 12px; line-height: 1.5; }
@media (max-width: 760px) { .section-heading, .heading-actions { align-items: flex-start; flex-direction: column; } .metric-grid, .detail-grid { grid-template-columns: repeat(2, 1fr); } }
</style>
