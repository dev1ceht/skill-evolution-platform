<script setup lang="ts">
import { onMounted, ref } from 'vue';
import type { AssistantTurn } from '../api/generated/client';
import type { CanteenScope, SmartCanteenApiPort } from '../api/smartCanteenApi';

const props = defineProps<{
  api: SmartCanteenApiPort;
  scope: CanteenScope;
  actorId?: string;
}>();

type Message = {
  id: string;
  role: 'user' | 'assistant';
  text: string;
  turn?: AssistantTurn;
};

const conversationId = conversationIdForScope(props.scope, props.actorId);
const message = ref('');
const messages = ref<Message[]>([]);
const loading = ref(false);
const error = ref('');
let historyLoadVersion = 0;

function newId(): string {
  if (typeof globalThis.crypto?.randomUUID === 'function') {
    return globalThis.crypto.randomUUID();
  }
  return `${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

function conversationIdForScope(scope: CanteenScope, actorId?: string): string {
  const key = `smart-canteen-assistant:${actorId ?? 'anonymous'}:${scope.schoolId}:${scope.canteenId}`;
  try {
    const existing = globalThis.localStorage?.getItem(key);
    if (existing) return existing;
    const created = `conversation-${newId()}`;
    globalThis.localStorage?.setItem(key, created);
    return created;
  } catch {
    return `conversation-${newId()}`;
  }
}

function messageOf(reason: unknown): string {
  return reason instanceof Error ? reason.message : '助手暂时无法处理，请稍后重试';
}

function resultSummary(turn: AssistantTurn): string {
  if (!turn.result) return '';
  const ingredient = typeof turn.result.ingredientName === 'string'
    ? turn.result.ingredientName
    : typeof turn.result.ingredientId === 'string' ? turn.result.ingredientId : '';
  const batch = typeof turn.result.batchId === 'string' ? turn.result.batchId : '';
  const supplier = typeof turn.result.supplierName === 'string'
    ? turn.result.supplierName
    : typeof turn.result.supplierId === 'string' ? turn.result.supplierId : '';
  const menuDate = typeof turn.result.menuDate === 'string' ? turn.result.menuDate : '';
  const mealTime = typeof turn.result.mealTime === 'string' ? turn.result.mealTime : '';
  const menuStatus = typeof turn.result.status === 'string' ? turn.result.status : '';
  const menuItems = Array.isArray(turn.result.items) ? `菜品 ${turn.result.items.length} 道` : '';
  return [
    ingredient,
    batch && `批次 ${batch}`,
    supplier && `供应商 ${supplier}`,
    menuDate && `日期 ${menuDate}`,
    mealTime && `餐次 ${mealTime}`,
    menuStatus && turn.intent === 'menu.query' && `状态 ${menuStatus}`,
    menuItems,
  ]
    .filter(Boolean)
    .join(' · ');
}

async function loadHistory(): Promise<void> {
  if (!props.api.getAssistantHistory) return;
  const requestVersion = ++historyLoadVersion;
  try {
    const history = await props.api.getAssistantHistory(conversationId, props.scope, 50);
    if (requestVersion !== historyLoadVersion) return;
    messages.value = history.turns.flatMap((entry) => [
      { id: `user-${entry.response.turnId}`, role: 'user' as const, text: entry.userMessage },
      {
        id: entry.response.turnId,
        role: 'assistant' as const,
        text: entry.response.message,
        turn: entry.response,
      },
    ]);
  } catch {
    // A history read is best-effort; a new message can still start the conversation.
  }
}

onMounted(() => {
  void loadHistory();
});

async function send(): Promise<void> {
  const text = message.value.trim();
  if (!text) {
    error.value = '请输入你要查询或办理的事项';
    return;
  }
  if (!props.api.sendAssistantMessage) {
    error.value = '当前客户端未接入智能助手';
    return;
  }
  historyLoadVersion += 1;
  loading.value = true;
  error.value = '';
  messages.value.push({ id: `user-${newId()}`, role: 'user', text });
  message.value = '';
  try {
    const turn = await props.api.sendAssistantMessage(
      conversationId,
      text,
      props.scope,
      `assistant-message-${newId()}`,
    );
    messages.value.push({
      id: turn.turnId,
      role: 'assistant',
      text: turn.message,
      turn,
    });
  } catch (reason) {
    error.value = messageOf(reason);
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <section class="assistant" data-testid="assistant-workspace">
    <div class="heading">
      <div>
        <p class="eyebrow">ASSISTANT PILOT · READ ONLY</p>
        <h2>智能业务助手</h2>
        <p class="description">支持食品溯源和日菜单只读查询。助手会识别意图、补齐必要信息，并关联可审计的 Agent Run。</p>
      </div>
      <span class="scope">{{ scope.schoolId }} · {{ scope.canteenId }}</span>
    </div>

    <div class="messages" aria-live="polite">
      <p v-if="!messages.length" class="empty" data-testid="assistant-empty">
        试试：查询 TRACE-001 的食品溯源，或查询 MENU-001 的菜单
      </p>
      <article
        v-for="item in messages"
        :key="item.id"
        class="message"
        :class="item.role"
        :data-testid="`assistant-message-${item.role}`"
      >
        <span class="role">{{ item.role === 'user' ? '你' : '助手' }}</span>
        <p>{{ item.text }}</p>
        <small v-if="item.turn?.runId">
          Run {{ item.turn.runId }} · {{ item.turn.runStatus }}
          <span v-if="resultSummary(item.turn)"> · {{ resultSummary(item.turn) }}</span>
        </small>
        <small v-if="item.turn?.missingFields.length">
          需要补充：{{ item.turn.missingFields.join('、') }}
        </small>
      </article>
    </div>

    <form class="composer" @submit.prevent="send">
      <label for="assistant-message">给助手发消息</label>
      <div class="composer-row">
        <textarea
          id="assistant-message"
          v-model="message"
          rows="2"
          maxlength="2000"
          placeholder="例如：查询 TRACE-001 的食品溯源"
          :disabled="loading"
        />
        <button type="submit" :disabled="loading">
          {{ loading ? '处理中…' : '发送' }}
        </button>
      </div>
    </form>
    <p v-if="error" class="error" data-testid="assistant-error">{{ error }}</p>
  </section>
</template>

<style scoped>
.assistant { margin: 0 0 24px; padding: 28px; border: 1px solid #c8d9ee; border-radius: 20px; background: rgba(247,251,255,.92); box-shadow: 0 16px 48px rgba(29,66,104,.08); }
.heading, .composer-row { display: flex; align-items: end; justify-content: space-between; gap: 16px; flex-wrap: wrap; }
.eyebrow { margin: 0 0 6px; color: #28669e; font-size: 11px; font-weight: 800; letter-spacing: .18em; }
h2 { margin: 0; font-size: 28px; }
.description, .empty, .scope, small { color: #687b8c; }
.scope { font-size: 12px; }
.messages { display: grid; gap: 10px; min-height: 90px; margin: 22px 0; }
.empty { margin: 0; padding: 18px; border-radius: 12px; background: #edf5fd; }
.message { max-width: 86%; padding: 12px 14px; border-radius: 14px; }
.message.user { justify-self: end; color: #fff; background: #28669e; }
.message.assistant { justify-self: start; color: #233746; background: #e6f1fb; }
.message p { margin: 4px 0; line-height: 1.5; }
.message small { display: block; line-height: 1.5; }
.message.user small { color: #d8ebfc; }
.role { font-size: 11px; font-weight: 800; }
.composer { display: grid; gap: 8px; color: #456175; font-size: 13px; font-weight: 700; }
textarea { flex: 1; min-width: 260px; resize: vertical; padding: 11px 12px; border: 1px solid #b9cee2; border-radius: 10px; font: inherit; color: #233746; }
button { padding: 11px 18px; border: 0; border-radius: 9px; color: white; background: #28669e; font-weight: 800; cursor: pointer; }
button:disabled, textarea:disabled { cursor: wait; opacity: .55; }
.error { margin: 12px 0 0; color: #8c2525; }
</style>
