<script setup lang="ts">
import axios from 'axios';
import { computed, ref } from 'vue';
import AgentTraceabilityWorkspace from './components/AgentTraceabilityWorkspace.vue';
import AgentMenuApprovalWorkspace from './components/AgentMenuApprovalWorkspace.vue';
import LoginPanel from './components/LoginPanel.vue';
import OperationsOverview from './components/OperationsOverview.vue';
import ProcurementPlanWorkspace from './components/ProcurementPlanWorkspace.vue';
import PurchaseOrderWorkspace from './components/PurchaseOrderWorkspace.vue';
import SafetyGovernanceWorkspace from './components/SafetyGovernanceWorkspace.vue';
import WorkflowDashboard from './components/WorkflowDashboard.vue';
import type { AuthSession, CanteenScope } from './api/smartCanteenApi';
import { SmartCanteenApi } from './api/smartCanteenApi';

const api = new SmartCanteenApi(axios.create({ timeout: 10_000 }));
const session = ref<AuthSession | null>(api.getSession());
// Keep the view state reactive; api.hasSession() is an imperative adapter query
// and would otherwise be evaluated only once by Vue's computed cache.
const authenticated = computed(() => session.value !== null);
const scope = ref<CanteenScope>(scopeFromSession(session.value));
// Kill switch for the pilot write entry. Production can disable the Agent menu
// without removing the legacy page paths by setting VITE_AGENT_MENU_ENABLED=false.
const agentMenuEnabled = import.meta.env.VITE_AGENT_MENU_ENABLED !== 'false';

function signedIn(next: AuthSession): void {
  session.value = next;
  scope.value = scopeFromSession(next);
}

async function signOut(): Promise<void> {
  try {
    await api.logout(session.value?.refreshToken);
  } finally {
    session.value = null;
  }
}

function scopeFromSession(value: AuthSession | null): CanteenScope {
  return {
    schoolId: value?.userInfo.schoolId ?? 'SCHOOL-001',
    canteenId: value?.userInfo.canteenId ?? 'CANTEEN-001',
  };
}
</script>

<template>
  <LoginPanel v-if="!authenticated" :api="api" @authenticated="signedIn" />
  <main v-else class="app-shell">
    <header class="account-bar">
      <span>当前用户：{{ session?.userInfo.nickname || session?.userInfo.username }}</span>
      <button type="button" @click="signOut">退出登录</button>
    </header>
    <OperationsOverview :api="api" :scope="scope" />
    <AgentTraceabilityWorkspace :api="api" :scope="scope" />
    <AgentMenuApprovalWorkspace v-if="agentMenuEnabled" :api="api" :scope="scope" />
    <ProcurementPlanWorkspace :api="api" :scope="scope" />
    <PurchaseOrderWorkspace :api="api" :scope="scope" />
    <WorkflowDashboard :api="api" :scope="scope" />
    <SafetyGovernanceWorkspace :api="api" :scope="scope" />
  </main>
</template>

<style scoped>
.app-shell { min-height: 100vh; }
.account-bar { display: flex; justify-content: flex-end; gap: 16px; align-items: center; padding: 16px clamp(24px, 6vw, 96px) 0; color: #5d6c64; font-size: 13px; }
button { padding: 8px 12px; border: 1px solid #c7d6ca; border-radius: 9px; color: #217a55; background: white; font-weight: 700; cursor: pointer; }
</style>
