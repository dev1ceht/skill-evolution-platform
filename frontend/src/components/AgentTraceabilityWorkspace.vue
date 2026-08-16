<script setup lang="ts">
import { ref } from 'vue';
import type { AgentRun } from '../api/generated/client';
import type { CanteenScope, SmartCanteenApiPort } from '../api/smartCanteenApi';

const props = defineProps<{
  api: SmartCanteenApiPort;
  scope: CanteenScope;
}>();

const traceCode = ref('');
const run = ref<AgentRun | null>(null);
const loading = ref(false);
const error = ref('');

function messageOf(reason: unknown): string {
  return reason instanceof Error ? reason.message : '溯源 Agent 查询失败';
}

function resultText(key: string): string {
  const value = run.value?.result?.[key];
  return typeof value === 'string' && value.length > 0 ? value : '—';
}

async function query(): Promise<void> {
  if (!props.api.startAgentTraceability) {
    error.value = '当前客户端未接入 Agent 溯源能力';
    return;
  }
  if (!traceCode.value.trim()) {
    error.value = '请输入批次溯源码';
    return;
  }
  loading.value = true;
  error.value = '';
  run.value = null;
  try {
    const key = `agent-trace-${traceCode.value.trim()}-${Date.now()}`;
    run.value = await props.api.startAgentTraceability(
      traceCode.value.trim(),
      props.scope,
      key,
    );
  } catch (reason) {
    error.value = messageOf(reason);
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <section class="agent-traceability" data-testid="agent-traceability">
    <div class="section-heading">
      <div>
        <p class="eyebrow">AGENT RUNTIME · READ ONLY</p>
        <h2>智能溯源查询</h2>
      </div>
      <span class="scope">{{ scope.schoolId }} · {{ scope.canteenId }}</span>
    </div>

    <form class="query-form" @submit.prevent="query">
      <label for="agent-trace-code">批次溯源码</label>
      <div class="query-row">
        <input
          id="agent-trace-code"
          v-model="traceCode"
          autocomplete="off"
          placeholder="例如 TRACE-001"
          :disabled="loading"
        />
        <button type="submit" :disabled="loading">
          {{ loading ? '查询中…' : '查询溯源' }}
        </button>
      </div>
    </form>

    <p v-if="loading" class="state" data-testid="agent-trace-loading">正在创建并执行只读 Run…</p>
    <p v-else-if="error" class="state error" data-testid="agent-trace-error">{{ error }}</p>
    <p v-else-if="!run" class="state" data-testid="agent-trace-empty">输入溯源码后查看运行结果</p>
    <div v-else class="result" data-testid="agent-trace-result">
      <div class="result-heading">
        <span>Run {{ run.runId }}</span>
        <strong :class="run.status.toLowerCase()">{{ run.status }}</strong>
      </div>
      <p v-if="run.status === 'SUCCEEDED' && run.result">
        {{ resultText('ingredientName') }} · 批次 {{ resultText('batchId') }} ·
        供应商 {{ resultText('supplierName') }}
      </p>
      <p v-else-if="run.errorMessage" class="error">{{ run.errorMessage }}</p>
      <small>Skill {{ run.skillId }}@{{ run.skillVersion }} · 计划摘要 {{ run.planHash.slice(0, 12) }}…</small>
    </div>
  </section>
</template>

<style scoped>
.agent-traceability { margin: 0 0 24px; padding: 24px 28px; border: 1px solid #d6e0d7; border-radius: 20px; background: rgba(255,255,255,.82); box-shadow: 0 16px 48px rgba(30,69,47,.08); }
.section-heading { display: flex; align-items: end; justify-content: space-between; gap: 16px; margin-bottom: 18px; }
.eyebrow { margin: 0 0 6px; color: #217a55; font-size: 11px; font-weight: 800; letter-spacing: .18em; }
h2 { margin: 0; font-size: 25px; }
.scope { color: #78847d; font-size: 12px; }
.query-form { display: grid; gap: 8px; margin-bottom: 18px; color: #5d6c64; font-size: 13px; font-weight: 700; }
.query-row { display: flex; gap: 10px; }
input { flex: 1; min-width: 0; padding: 11px 12px; border: 1px solid #c7d6ca; border-radius: 9px; font: inherit; color: #17231d; }
button { padding: 10px 16px; border: 0; border-radius: 9px; color: white; background: #217a55; font-weight: 700; cursor: pointer; }
button:disabled, input:disabled { cursor: wait; opacity: .65; }
.state { margin: 0; color: #78847d; }
.state.error, .error { color: #8c2525; }
.result { display: grid; gap: 8px; padding: 16px; border-radius: 14px; background: #e6f3eb; color: #17231d; }
.result-heading { display: flex; justify-content: space-between; gap: 12px; font-size: 13px; }
.result-heading strong { color: #217a55; }
.result-heading strong.failed { color: #8c2525; }
.result p { margin: 0; line-height: 1.5; }
.result small { color: #78847d; }
</style>
