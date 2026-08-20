<script setup lang="ts">
import { ref } from 'vue';
import type { AuthSession, SmartCanteenApi } from '../api/smartCanteenApi';

const props = defineProps<{ api: SmartCanteenApi }>();
const emit = defineEmits<{ authenticated: [session: AuthSession] }>();

const username = ref('admin');
const password = ref('');
const loading = ref(false);
const error = ref('');

async function login(): Promise<void> {
  loading.value = true;
  error.value = '';
  try {
    const session = await props.api.login(username.value, password.value);
    emit('authenticated', session);
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : '登录失败';
  } finally {
    loading.value = false;
  }
}

</script>

<template>
  <main class="login-shell">
    <section class="login-card">
      <p class="eyebrow">SMART CANTEEN</p>
      <h1>智慧食堂运营台</h1>
      <p class="subtitle">登录后查看菜单、采购、库存、台账和风险指标。</p>
      <form @submit.prevent="login">
        <label>
          用户名
          <input v-model="username" autocomplete="username" required />
        </label>
        <label>
          密码
          <input v-model="password" type="password" autocomplete="current-password" required />
        </label>
        <p v-if="error" class="error" data-testid="login-error">{{ error }}</p>
        <button type="submit" :disabled="loading">
          {{ loading ? '登录中…' : '登录运营台' }}
        </button>
      </form>
      <small>请由部署人员通过 BOOTSTRAP_ADMIN_PASSWORD 配置首个管理员。</small>
    </section>
  </main>
</template>

<style scoped>
.login-shell { display: grid; min-height: 100vh; place-items: center; padding: 24px; background: #eef3ee; }
.login-card { width: min(440px, 100%); padding: 38px; border: 1px solid #d6e0d7; border-radius: 24px; background: rgba(255,255,255,.9); box-shadow: 0 24px 70px rgba(30,69,47,.12); }
.eyebrow { margin: 0 0 10px; color: #217a55; font-size: 12px; font-weight: 800; letter-spacing: .18em; }
h1 { margin: 0 0 12px; font-size: 36px; }
.subtitle { margin: 0 0 28px; color: #5d6c64; line-height: 1.6; }
form { display: grid; gap: 16px; }
label { display: grid; gap: 7px; color: #33483b; font-size: 14px; font-weight: 700; }
input { padding: 12px 14px; border: 1px solid #cad8cd; border-radius: 10px; font: inherit; }
button { padding: 12px 16px; border: 0; border-radius: 10px; color: white; background: #217a55; font-weight: 800; cursor: pointer; }
button:disabled { cursor: not-allowed; opacity: .5; }
.error { margin: 0; padding: 10px 12px; border-radius: 9px; color: #8c2525; background: #feecec; }
small { display: block; margin-top: 18px; color: #78847d; line-height: 1.5; }
</style>
