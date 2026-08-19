<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import type { AgentRun, AgentRunEvent, DailyMenu } from '../api/generated/client';
import type { CanteenScope, SmartCanteenApiPort } from '../api/smartCanteenApi';

const props = defineProps<{
  api: SmartCanteenApiPort;
  scope: CanteenScope;
}>();

const menuId = ref('');
const menuVersion = ref(0);
const intent = ref<'menu.submit' | 'menu.record-decision' | 'menu.publish'>('menu.submit');
const decision = ref<'APPROVE' | 'REJECT'>('APPROVE');
const comment = ref('');
const run = ref<AgentRun | null>(null);
const menuState = ref<DailyMenu | null>(null);
const events = ref<AgentRunEvent[]>([]);
const idempotencyKey = ref('');
const loading = ref(false);
const error = ref('');
const hasPlan = computed(() => run.value !== null);
const planParameters = computed<Record<string, unknown> | null>(() => {
  const parameters = run.value?.plan?.businessParameters;
  return parameters && typeof parameters === 'object'
    ? parameters as Record<string, unknown>
    : null;
});
const planMenuId = computed(() => String(planParameters.value?.menuId ?? '未提供'));
const planMenuVersion = computed(() => String(planParameters.value?.menuVersion ?? '未提供'));
const planDecision = computed(() => String(planParameters.value?.decision ?? '无'));
const domainNextStep = computed(() => {
  switch (menuState.value?.status) {
    case 'DRAFT':
    case 'REJECTED':
      return '可提交领域审批';
    case 'PENDING_APPROVAL':
      return '等待领域审批人处理';
    case 'APPROVED':
      return '可由不同操作者发布';
    case 'PUBLISHED':
      return '已发布，不可重复发布';
    default:
      return '执行前由后端再次校验';
  }
});

watch([menuId, menuVersion], () => {
  menuState.value = null;
  idempotencyKey.value = '';
});

watch([intent, decision, comment], () => {
  // A changed business payload is a new command; an unchanged payload reuses the
  // same key so a network retry cannot create a second Run.
  idempotencyKey.value = '';
});

function messageOf(reason: unknown): string {
  return reason instanceof Error ? reason.message : '菜单 Agent 操作失败';
}

async function refreshEvents(nextRun: AgentRun): Promise<void> {
  if (!props.api.getAgentRunEvents) return;
  events.value = await props.api.getAgentRunEvents(nextRun.runId, props.scope);
}

async function start(): Promise<void> {
  if (!props.api.startAgentRun) {
    error.value = '当前客户端未接入菜单 Agent 能力';
    return;
  }
  if (!menuId.value.trim()) {
    error.value = '请输入日菜单 ID';
    return;
  }
  loading.value = true;
  error.value = '';
  try {
    if (props.api.getDailyMenu) {
      menuState.value = await props.api.getDailyMenu(menuId.value.trim(), props.scope);
      if (menuState.value.version !== menuVersion.value) {
        throw new Error(
          `菜单版本已变化：当前为 ${menuState.value.version}，输入为 ${menuVersion.value}`,
        );
      }
    }
    const input: Record<string, unknown> = {
      menuId: menuId.value.trim(),
      menuVersion: menuVersion.value,
    };
    if (intent.value === 'menu.record-decision') {
      input.decision = decision.value;
      if (comment.value.trim()) input.comment = comment.value.trim();
    }
    if (!idempotencyKey.value) {
      idempotencyKey.value = `agent-menu-${intent.value}-${menuId.value.trim()}-${menuVersion.value}-${Date.now()}`;
    }
    run.value = await props.api.startAgentRun(
      intent.value,
      input,
      props.scope,
      idempotencyKey.value,
    );
    await refreshEvents(run.value);
  } catch (reason) {
    error.value = messageOf(reason);
  } finally {
    loading.value = false;
  }
}

async function decide(decisionType: 'RUN_CONFIRM' | 'RUN_REJECT' | 'RUN_CANCEL'): Promise<void> {
  if (!run.value || !props.api.decideAgentRun) return;
  loading.value = true;
  error.value = '';
  try {
    run.value = await props.api.decideAgentRun(
      run.value.runId,
      decisionType,
      run.value.version,
      props.scope,
      decisionType === 'RUN_REJECT' ? 'operator rejected the plan' : undefined,
      undefined,
      `agent-menu-decision-${run.value.runId}-${run.value.version}-${decisionType}`,
    );
    await refreshEvents(run.value);
  } catch (reason) {
    error.value = messageOf(reason);
  } finally {
    loading.value = false;
  }
}

function reset(): void {
  run.value = null;
  menuState.value = null;
  events.value = [];
  error.value = '';
  idempotencyKey.value = '';
}
</script>

<template>
  <section class="agent-menu" data-testid="agent-menu-approval">
    <div class="section-heading">
      <div>
        <p class="eyebrow">AGENT RUNTIME · WRITE WITH CONFIRMATION</p>
        <h2>菜单提交 / 审批 / 发布</h2>
      </div>
      <span class="scope">{{ scope.schoolId }} · {{ scope.canteenId }}</span>
    </div>

    <form class="menu-form" @submit.prevent="start">
      <label for="agent-menu-id">日菜单 ID</label>
      <input id="agent-menu-id" v-model="menuId" placeholder="例如 M001" :disabled="loading || hasPlan" />
      <div class="form-grid">
        <label>菜单版本 <input v-model.number="menuVersion" type="number" min="0" :disabled="loading || hasPlan" /></label>
        <label>操作
          <select v-model="intent" :disabled="loading || hasPlan">
            <option value="menu.submit">提交审批</option>
            <option value="menu.record-decision">记录领域审批</option>
            <option value="menu.publish">发布已批准菜单</option>
          </select>
        </label>
      </div>
      <label v-if="intent === 'menu.record-decision'">审批结果
        <select v-model="decision" :disabled="loading || hasPlan">
          <option value="APPROVE">批准</option>
          <option value="REJECT">拒绝</option>
        </select>
      </label>
      <label v-if="intent === 'menu.record-decision'">审批备注
        <textarea v-model="comment" maxlength="500" :disabled="loading || hasPlan" />
      </label>
      <button type="submit" :disabled="loading || hasPlan">{{ loading ? '处理中…' : '生成计划' }}</button>
    </form>

    <p v-if="error" class="state error" data-testid="agent-menu-error">{{ error }}</p>
    <div v-if="run" class="run-card" data-testid="agent-menu-run">
      <div class="run-heading"><span>Run {{ run.runId }}</span><strong>{{ run.status }}</strong></div>
      <p>计划版本 {{ run.version }} · Skill {{ run.skillId }}@{{ run.skillVersion }}</p>
      <div class="domain-state" data-testid="agent-menu-domain-state">
        <strong>领域审批状态</strong>
        <p>当前状态：{{ menuState?.status ?? '未读取' }} · 下一步：{{ domainNextStep }}</p>
        <p v-if="menuState?.submittedBy">提交人：{{ menuState.submittedBy }}</p>
        <p v-if="menuState?.decisionBy">审批人：{{ menuState.decisionBy }}</p>
        <p v-if="menuState?.publishedBy">发布人：{{ menuState.publishedBy }}</p>
      </div>
      <div v-if="run.status === 'WAITING_CONFIRMATION'" class="plan-summary" data-testid="agent-menu-plan-summary">
        <strong>不可变计划摘要</strong>
        <p>目标菜单 {{ planMenuId }} · 菜单版本 {{ planMenuVersion }} · 运行意图 {{ run.intent }}</p>
        <p v-if="run.intent === 'menu.record-decision'">领域审批决策：{{ planDecision }}</p>
      </div>
      <p v-if="run.result?.status">领域状态：{{ run.result.status }}（与运行确认独立）</p>
      <div v-if="run.status === 'WAITING_CONFIRMATION'" class="actions">
        <button type="button" :disabled="loading" @click="decide('RUN_CONFIRM')">确认执行</button>
        <button type="button" class="secondary" :disabled="loading" @click="decide('RUN_REJECT')">拒绝计划</button>
      </div>
      <button v-if="run.status === 'PLANNED'" type="button" class="secondary" :disabled="loading" @click="decide('RUN_CANCEL')">取消运行</button>
      <button type="button" class="secondary" :disabled="loading" @click="reset">新建计划</button>
      <p v-if="run.errorMessage" class="error">{{ run.errorMessage }}</p>
      <ol v-if="events.length" class="events">
        <li v-for="event in events" :key="event.eventId">{{ event.eventType }} · {{ event.toStatus }}</li>
      </ol>
    </div>
  </section>
</template>

<style scoped>
.agent-menu { margin: 0 0 24px; padding: 24px 28px; border: 1px solid #ead7b7; border-radius: 20px; background: rgba(255,252,245,.9); box-shadow: 0 16px 48px rgba(91,67,30,.08); }
.section-heading { display: flex; align-items: end; justify-content: space-between; gap: 16px; margin-bottom: 18px; }
.eyebrow { margin: 0 0 6px; color: #a56c1d; font-size: 11px; font-weight: 800; letter-spacing: .18em; }
h2 { margin: 0; font-size: 25px; }
.scope { color: #78847d; font-size: 12px; }
.menu-form { display: grid; gap: 9px; color: #5d6c64; font-size: 13px; font-weight: 700; }
.form-grid { display: grid; grid-template-columns: 1fr 2fr; gap: 10px; }
input, select, textarea { box-sizing: border-box; width: 100%; margin-top: 5px; padding: 10px 11px; border: 1px solid #d9c8a9; border-radius: 9px; font: inherit; color: #17231d; background: #fff; }
textarea { min-height: 66px; resize: vertical; }
button { width: fit-content; padding: 10px 16px; border: 0; border-radius: 9px; color: white; background: #a56c1d; font-weight: 700; cursor: pointer; }
button.secondary { color: #7d561d; background: #f3e5cd; }
button:disabled, input:disabled, select:disabled, textarea:disabled { cursor: wait; opacity: .65; }
.state { margin: 14px 0 0; color: #78847d; }
.error { color: #8c2525; }
.run-card { display: grid; gap: 9px; margin-top: 18px; padding: 16px; border-radius: 14px; background: #fff5e5; color: #17231d; }
.run-heading { display: flex; justify-content: space-between; gap: 12px; font-size: 13px; }
.run-heading strong { color: #a56c1d; }
.run-card p { margin: 0; line-height: 1.5; }
.plan-summary { display: grid; gap: 4px; padding: 10px 12px; border: 1px solid #e1c48e; border-radius: 10px; background: #fffaf0; }
.plan-summary strong { color: #7d561d; font-size: 12px; }
.domain-state { display: grid; gap: 4px; padding: 10px 12px; border: 1px solid #c7ddd0; border-radius: 10px; background: #f5fbf7; }
.domain-state strong { color: #217a55; font-size: 12px; }
.actions { display: flex; gap: 9px; }
.events { margin: 4px 0 0; padding-left: 20px; color: #6e756f; font-size: 12px; }
</style>
