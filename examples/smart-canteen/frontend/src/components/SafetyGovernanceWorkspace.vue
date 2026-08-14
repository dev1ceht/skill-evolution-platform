<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import type {
  CanteenShowcase,
  ComplianceRecord,
  ConfiguredLedgerCycle,
  LedgerConfiguration,
  MealSuspension,
  SupplierComplaint,
} from '../api/generated/client';
import type { CanteenScope, SmartCanteenApiPort } from '../api/smartCanteenApi';

const props = defineProps<{
  api: SmartCanteenApiPort;
  scope: CanteenScope;
}>();

const configurations = ref<LedgerConfiguration[]>([]);
const cycles = ref<ConfiguredLedgerCycle[]>([]);
const complianceRecords = ref<ComplianceRecord[]>([]);
const showcases = ref<CanteenShowcase[]>([]);
const suspensions = ref<MealSuspension[]>([]);
const complaints = ref<SupplierComplaint[]>([]);
const loading = ref(true);
const busy = ref(false);
const error = ref('');
const notice = ref('');
const expiryWindow = ref(30);
const ledgerCycleId = ref('');
const ledgerJson = ref('{}');
const complaintReply = ref('已完成核查并提交整改要求。');

const complianceForm = ref({
  category: 'LICENSE' as ComplianceRecord['category'],
  subjectType: 'CANTEEN',
  subjectId: props.scope.canteenId,
  subjectName: props.scope.canteenId,
  title: '食品经营许可证',
  credentialNo: '',
  validFrom: today(),
  validTo: plusDays(365),
});

const showcaseForm = ref({
  title: '本周食堂风采',
  content: '展示食堂环境、陪餐记录和本周食品安全管理工作。',
});

const suspensionForm = ref({
  mealDate: plusDays(1),
  mealPeriod: 'LUNCH' as MealSuspension['mealPeriod'],
  reason: '设备维护，午餐暂停供应。',
});

const complaintForm = ref({
  supplierId: '',
  subject: '食材质量问题',
  description: '请补充问题食材批次、数量和现场处置情况。',
});

const missingCycleCount = computed(
  () => cycles.value.filter((cycle) => cycle.missingLedgerCodes.length > 0).length,
);
const expiringCount = computed(
  () => complianceRecords.value.filter((record) => daysUntil(record.validTo) <= expiryWindow.value).length,
);
const openComplaintCount = computed(
  () => complaints.value.filter((complaint) => !['CLOSED', 'REJECTED'].includes(complaint.status)).length,
);
const publishedShowcaseCount = computed(
  () => showcases.value.filter((showcase) => showcase.status === 'PUBLISHED').length,
);

function today(): string {
  return new Date().toISOString().slice(0, 10);
}

function plusDays(days: number): string {
  const date = new Date();
  date.setDate(date.getDate() + days);
  return date.toISOString().slice(0, 10);
}

function daysUntil(value: string): number {
  const target = new Date(`${value}T00:00:00`).getTime();
  return Math.ceil((target - new Date(`${today()}T00:00:00`).getTime()) / 86_400_000);
}

function messageOf(reason: unknown): string {
  return reason instanceof Error ? reason.message : '操作失败，请检查接口和输入内容';
}

function selectedCycle(): ConfiguredLedgerCycle | undefined {
  return cycles.value.find((cycle) => cycle.cycleId === ledgerCycleId.value);
}

function selectCycle(cycle: ConfiguredLedgerCycle): void {
  ledgerCycleId.value = cycle.cycleId;
  const configuration = configurations.value.find(
    (item) => item.id === cycle.configurationId,
  );
  const payload: Record<string, unknown> = {
    confirmedBy: 'web-console',
    confirmedAt: new Date().toISOString(),
  };
  for (const field of configuration?.requiredFields ?? []) {
    payload[field] = '';
  }
  ledgerJson.value = JSON.stringify(payload, null, 2);
}

function chooseCycle(): void {
  const cycle = selectedCycle();
  if (cycle) selectCycle(cycle);
}

async function load(): Promise<void> {
  loading.value = true;
  error.value = '';
  try {
    const [nextConfigurations, nextCycles, nextCompliance, nextShowcases, nextSuspensions, nextComplaints] =
      await Promise.all([
        props.api.listLedgerConfigurations
          ? props.api.listLedgerConfigurations(props.scope)
          : Promise.resolve([]),
        props.api.ensureConfiguredLedgerCycles
          ? props.api.ensureConfiguredLedgerCycles(props.scope)
          : Promise.resolve([]),
        props.api.listComplianceRecords
          ? props.api.listComplianceRecords(props.scope)
          : Promise.resolve([]),
        props.api.listCanteenShowcases
          ? props.api.listCanteenShowcases(props.scope)
          : Promise.resolve([]),
        props.api.listMealSuspensions
          ? props.api.listMealSuspensions(props.scope)
          : Promise.resolve([]),
        props.api.listSupplierComplaints
          ? props.api.listSupplierComplaints(props.scope)
          : Promise.resolve([]),
      ]);
    configurations.value = nextConfigurations;
    cycles.value = nextCycles;
    complianceRecords.value = nextCompliance;
    showcases.value = nextShowcases;
    suspensions.value = nextSuspensions;
    complaints.value = nextComplaints;
    if (!selectedCycle() && cycles.value[0]) {
      selectCycle(cycles.value[0]);
    }
  } catch (reason) {
    error.value = messageOf(reason);
  } finally {
    loading.value = false;
  }
}

async function perform(action: () => Promise<void>, success: string): Promise<void> {
  busy.value = true;
  error.value = '';
  notice.value = '';
  try {
    await action();
    notice.value = success;
  } catch (reason) {
    error.value = messageOf(reason);
  } finally {
    busy.value = false;
  }
}

async function completeLedger(): Promise<void> {
  await perform(async () => {
    const cycle = selectedCycle();
    if (!cycle || !props.api.completeConfiguredLedger) {
      throw new Error('当前没有可填写的配置化台账周期');
    }
    let content: Record<string, unknown>;
    try {
      content = JSON.parse(ledgerJson.value) as Record<string, unknown>;
    } catch {
      throw new Error('台账内容必须是合法 JSON');
    }
    await props.api.completeConfiguredLedger(
      cycle.cycleId,
      { ledgerCode: cycle.ledgerCode, content },
      props.scope,
    );
    cycles.value = await props.api.ensureConfiguredLedgerCycles!(props.scope);
    const refreshed = cycles.value.find((item) => item.cycleId === cycle.cycleId);
    if (refreshed) selectCycle(refreshed);
  }, '台账记录已保存，缺项预警已重新计算');
}

async function scanExpiry(): Promise<void> {
  await perform(async () => {
    if (!props.api.scanComplianceExpiry) throw new Error('当前客户端未提供资质扫描能力');
    await props.api.scanComplianceExpiry(props.scope, { windowDays: expiryWindow.value });
    if (props.api.listComplianceRecords) {
      complianceRecords.value = await props.api.listComplianceRecords(props.scope);
    }
  }, '资质临期扫描完成');
}

async function createCompliance(): Promise<void> {
  await perform(async () => {
    if (!props.api.createComplianceRecord) throw new Error('当前客户端未提供资质档案能力');
    await props.api.createComplianceRecord(
      { ...complianceForm.value, attachmentRefs: [] },
      props.scope,
    );
    if (props.api.listComplianceRecords) {
      complianceRecords.value = await props.api.listComplianceRecords(props.scope);
    }
  }, '资质档案已保存为草稿');
}

async function advanceCompliance(record: ComplianceRecord): Promise<void> {
  await perform(async () => {
    if (record.status === 'DRAFT' || record.status === 'REJECTED') {
      if (!props.api.submitComplianceRecord) throw new Error('当前客户端未提供资质提交能力');
      await props.api.submitComplianceRecord(record.id, record.version, props.scope);
    } else if (record.status === 'SUBMITTED') {
      if (!props.api.reviewComplianceRecord) throw new Error('当前客户端未提供资质审核能力');
      await props.api.reviewComplianceRecord(
        record.id,
        { version: record.version, status: 'APPROVED', reviewRemark: '资料核验通过' },
        props.scope,
      );
    }
    if (props.api.listComplianceRecords) {
      complianceRecords.value = await props.api.listComplianceRecords(props.scope);
    }
  }, record.status === 'SUBMITTED' ? '资质已审核通过' : '资质已提交审核');
}

async function createShowcase(): Promise<void> {
  await perform(async () => {
    if (!props.api.createCanteenShowcase) throw new Error('当前客户端未提供风采发布能力');
    await props.api.createCanteenShowcase(
      { ...showcaseForm.value, photos: [] },
      props.scope,
    );
    await reloadShowcases();
  }, '食堂风采草稿已保存');
}

async function advanceShowcase(showcase: CanteenShowcase): Promise<void> {
  await perform(async () => {
    if (showcase.status === 'DRAFT' || showcase.status === 'REJECTED') {
      if (!props.api.submitCanteenShowcase) throw new Error('当前客户端未提供风采提交能力');
      await props.api.submitCanteenShowcase(showcase.id, showcase.version, props.scope);
    } else if (showcase.status === 'SUBMITTED') {
      if (!props.api.reviewCanteenShowcase) throw new Error('当前客户端未提供风采审核能力');
      await props.api.reviewCanteenShowcase(
        showcase.id,
        { version: showcase.version, status: 'APPROVED', reviewRemark: '内容审核通过' },
        props.scope,
      );
    } else if (showcase.status === 'APPROVED') {
      if (!props.api.publishCanteenShowcase) throw new Error('当前客户端未提供风采发布能力');
      await props.api.publishCanteenShowcase(showcase.id, showcase.version, props.scope);
    }
    await reloadShowcases();
  }, '风采状态已推进');
}

async function reloadShowcases(): Promise<void> {
  if (!props.api.listCanteenShowcases) return;
  showcases.value = await props.api.listCanteenShowcases(props.scope);
}

async function createSuspension(): Promise<void> {
  await perform(async () => {
    if (!props.api.createMealSuspension) throw new Error('当前客户端未提供停餐报备能力');
    await props.api.createMealSuspension(suspensionForm.value, props.scope);
    if (props.api.listMealSuspensions) {
      suspensions.value = await props.api.listMealSuspensions(props.scope);
    }
  }, '停餐报备已提交');
}

async function advanceSuspension(suspension: MealSuspension): Promise<void> {
  await perform(async () => {
    if (suspension.status === 'SUBMITTED') {
      if (!props.api.reviewMealSuspension) throw new Error('当前客户端未提供停餐审核能力');
      await props.api.reviewMealSuspension(
        suspension.id,
        { version: suspension.version, status: 'APPROVED', reviewRemark: '停餐原因核验通过' },
        props.scope,
      );
    } else if (suspension.status === 'APPROVED') {
      if (!props.api.cancelMealSuspension) throw new Error('当前客户端未提供停餐撤销能力');
      await props.api.cancelMealSuspension(suspension.id, suspension.version, props.scope);
    }
    if (props.api.listMealSuspensions) {
      suspensions.value = await props.api.listMealSuspensions(props.scope);
    }
  }, suspension.status === 'SUBMITTED' ? '停餐报备已审核' : '停餐报备已撤销');
}

async function createComplaint(): Promise<void> {
  await perform(async () => {
    if (!props.api.createSupplierComplaint) throw new Error('当前客户端未提供供应商投诉能力');
    await props.api.createSupplierComplaint(
      { ...complaintForm.value, attachmentRefs: [] },
      props.scope,
    );
    if (props.api.listSupplierComplaints) {
      complaints.value = await props.api.listSupplierComplaints(props.scope);
    }
  }, '供应商投诉已登记');
}

async function advanceComplaint(complaint: SupplierComplaint): Promise<void> {
  await perform(async () => {
    if (complaint.status === 'SUBMITTED') {
      if (!props.api.reviewSupplierComplaint) throw new Error('当前客户端未提供投诉受理能力');
      await props.api.reviewSupplierComplaint(
        complaint.id,
        { version: complaint.version, status: 'ACCEPTED', note: '投诉信息完整，已受理' },
        props.scope,
      );
    } else if (complaint.status === 'ACCEPTED') {
      if (!props.api.processSupplierComplaint) throw new Error('当前客户端未提供投诉处理能力');
      await props.api.processSupplierComplaint(complaint.id, complaint.version, props.scope);
    } else if (complaint.status === 'PROCESSING') {
      if (!props.api.replySupplierComplaint) throw new Error('当前客户端未提供投诉回复能力');
      await props.api.replySupplierComplaint(
        complaint.id,
        { version: complaint.version, reply: complaintReply.value },
        props.scope,
      );
    } else if (complaint.status === 'REPLIED') {
      if (!props.api.closeSupplierComplaint) throw new Error('当前客户端未提供投诉关闭能力');
      await props.api.closeSupplierComplaint(complaint.id, complaint.version, props.scope);
    }
    if (props.api.listSupplierComplaints) {
      complaints.value = await props.api.listSupplierComplaints(props.scope);
    }
  }, '投诉状态已推进');
}

function actionLabel(status: string): string {
  return {
    DRAFT: '提交审核',
    REJECTED: '重新提交',
    SUBMITTED: '审核通过',
    APPROVED: '发布',
    ACCEPTED: '开始处理',
    PROCESSING: '发送回复',
    REPLIED: '关闭投诉',
  }[status] ?? '推进状态';
}

watch(
  () => `${props.scope.schoolId}:${props.scope.canteenId}`,
  () => void load(),
);
onMounted(load);
</script>

<template>
  <section class="governance" data-testid="safety-governance-workspace">
    <header class="section-heading">
      <div>
        <p class="eyebrow">PHASE 3 · SAFETY &amp; GOVERNANCE</p>
        <h2>食品安全合规与运营监管</h2>
        <p class="subtitle">当前范围：{{ props.scope.schoolId }} · {{ props.scope.canteenId }}</p>
      </div>
      <button type="button" class="secondary" :disabled="loading || busy" @click="load">刷新监管数据</button>
    </header>

    <p v-if="loading" class="state" data-testid="governance-loading">正在加载监管数据…</p>
    <p v-if="error" class="state error" data-testid="governance-error">{{ error }}</p>
    <p v-if="notice" class="state success" data-testid="governance-notice">{{ notice }}</p>

    <div class="metric-grid">
      <article class="metric"><span>配置化台账缺项</span><strong>{{ missingCycleCount }}</strong><small>按当前周期生成</small></article>
      <article class="metric" :class="{ warning: expiringCount > 0 }"><span>资质临期档案</span><strong>{{ expiringCount }}</strong><small>{{ expiryWindow }} 天内到期</small></article>
      <article class="metric"><span>已发布风采</span><strong>{{ publishedShowcaseCount }}</strong><small>对外展示版本</small></article>
      <article class="metric" :class="{ warning: openComplaintCount > 0 }"><span>未关闭投诉</span><strong>{{ openComplaintCount }}</strong><small>供应商整改闭环</small></article>
    </div>

    <div class="workspace-grid">
      <article class="card wide">
        <div class="card-heading">
          <div><p class="eyebrow">01 · LEDGER</p><h3>可配置台账与周期缺项</h3></div>
          <span>{{ configurations.length }} 个启用配置</span>
        </div>
        <div class="form-grid">
          <label>待填写周期
            <select v-model="ledgerCycleId" @change="chooseCycle">
              <option value="">请选择周期</option>
              <option v-for="cycle in cycles" :key="cycle.cycleId" :value="cycle.cycleId">
                {{ cycle.ledgerCode }} · {{ cycle.periodStart }} 至 {{ cycle.periodEnd }}
              </option>
            </select>
          </label>
          <label class="span-2">记录内容（JSON）
            <textarea v-model="ledgerJson" rows="4" spellcheck="false" />
          </label>
        </div>
        <button type="button" :disabled="busy || !ledgerCycleId" @click="completeLedger">保存台账记录</button>
        <ul class="compact-list">
          <li v-for="cycle in cycles" :key="cycle.cycleId">
            <button type="button" class="link-button" @click="selectCycle(cycle)">{{ cycle.ledgerCode }}</button>
            <span>{{ cycle.periodStart }} – {{ cycle.periodEnd }}</span>
            <strong :class="{ danger: cycle.missingLedgerCodes.length }">
              {{ cycle.missingLedgerCodes.length ? `缺 ${cycle.missingLedgerCodes.join('、')}` : '已完成' }}
            </strong>
          </li>
        </ul>
      </article>

      <article class="card">
        <div class="card-heading"><div><p class="eyebrow">02 · COMPLIANCE</p><h3>食品安全资质档案</h3></div></div>
        <div class="form-grid">
          <label>类别<select v-model="complianceForm.category"><option value="LICENSE">许可证</option><option value="HEALTH_CERTIFICATE">健康证</option><option value="MANAGEMENT_DOCUMENT">管理制度</option><option value="SUPPLIER_QUALIFICATION">供应商资质</option></select></label>
          <label>证照名称<input v-model="complianceForm.title" /></label>
          <label>编号<input v-model="complianceForm.credentialNo" /></label>
          <label>有效期至<input v-model="complianceForm.validTo" type="date" /></label>
        </div>
        <div class="actions"><button type="button" :disabled="busy" @click="createCompliance">保存资质草稿</button><button type="button" class="secondary" :disabled="busy" @click="scanExpiry">扫描临期</button></div>
        <ul class="record-list">
          <li v-for="record in complianceRecords" :key="record.id"><div><strong>{{ record.title }}</strong><span>{{ record.category }} · {{ record.validTo }}</span></div><div class="record-action"><em>{{ record.status }}</em><button v-if="['DRAFT', 'REJECTED', 'SUBMITTED'].includes(record.status)" type="button" class="link-button" :disabled="busy" @click="advanceCompliance(record)">{{ actionLabel(record.status) }}</button></div></li>
        </ul>
      </article>

      <article class="card">
        <div class="card-heading"><div><p class="eyebrow">03 · SHOWCASE</p><h3>食堂风采发布</h3></div></div>
        <div class="form-grid"><label>标题<input v-model="showcaseForm.title" /></label><label class="span-2">内容<textarea v-model="showcaseForm.content" rows="3" /></label></div>
        <button type="button" :disabled="busy" @click="createShowcase">保存风采草稿</button>
        <ul class="record-list"><li v-for="showcase in showcases" :key="showcase.id"><div><strong>{{ showcase.title }}</strong><span>{{ showcase.status }} · {{ showcase.updatedAt || showcase.createdAt }}</span></div><button v-if="['DRAFT', 'REJECTED', 'SUBMITTED', 'APPROVED'].includes(showcase.status)" type="button" class="link-button" :disabled="busy" @click="advanceShowcase(showcase)">{{ actionLabel(showcase.status) }}</button></li></ul>
      </article>

      <article class="card">
        <div class="card-heading"><div><p class="eyebrow">04 · MEAL SUSPENSION</p><h3>停餐报备</h3></div></div>
        <div class="form-grid"><label>日期<input v-model="suspensionForm.mealDate" type="date" /></label><label>餐次<select v-model="suspensionForm.mealPeriod"><option value="BREAKFAST">早餐</option><option value="LUNCH">午餐</option><option value="DINNER">晚餐</option><option value="SNACK">加餐</option></select></label><label class="span-2">原因<input v-model="suspensionForm.reason" /></label></div>
        <button type="button" :disabled="busy" @click="createSuspension">提交停餐报备</button>
        <ul class="record-list"><li v-for="suspension in suspensions" :key="suspension.id"><div><strong>{{ suspension.mealDate }} · {{ suspension.mealPeriod }}</strong><span>{{ suspension.reason }}</span></div><div class="record-action"><em>{{ suspension.status }}</em><button v-if="['SUBMITTED', 'APPROVED'].includes(suspension.status)" type="button" class="link-button" :disabled="busy" @click="advanceSuspension(suspension)">{{ suspension.status === 'APPROVED' ? '撤销' : '审核通过' }}</button></div></li></ul>
      </article>

      <article class="card wide">
        <div class="card-heading"><div><p class="eyebrow">05 · SUPPLIER COMPLAINT</p><h3>供应商投诉闭环</h3></div></div>
        <div class="form-grid"><label>供应商 ID<input v-model="complaintForm.supplierId" placeholder="SUPPLIER-001" /></label><label>主题<input v-model="complaintForm.subject" /></label><label class="span-2">问题描述<textarea v-model="complaintForm.description" rows="3" /></label><label class="span-2">处理回复<textarea v-model="complaintReply" rows="2" /></label></div>
        <button type="button" :disabled="busy" @click="createComplaint">登记投诉</button>
        <ul class="record-list"><li v-for="complaint in complaints" :key="complaint.id"><div><strong>{{ complaint.subject }}</strong><span>{{ complaint.supplierId }} · {{ complaint.description }}</span></div><div class="record-action"><em>{{ complaint.status }}</em><button v-if="['SUBMITTED', 'ACCEPTED', 'PROCESSING', 'REPLIED'].includes(complaint.status)" type="button" class="link-button" :disabled="busy" @click="advanceComplaint(complaint)">{{ actionLabel(complaint.status) }}</button></div></li></ul>
      </article>
    </div>
  </section>
</template>

<style scoped>
.governance { margin: 0 0 36px; padding: 28px; border: 1px solid #d6e0d7; border-radius: 24px; background: rgba(255,255,255,.86); box-shadow: 0 16px 48px rgba(30,69,47,.08); }
.section-heading, .card-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.section-heading { margin-bottom: 22px; }
.eyebrow { margin: 0 0 6px; color: #217a55; font-size: 11px; font-weight: 800; letter-spacing: .16em; }
h2, h3 { margin: 0; color: #17231d; }
h2 { font-size: 28px; } h3 { font-size: 20px; }
.subtitle, .card-heading > span, small, .record-list span, .compact-list span { color: #78847d; font-size: 12px; }
.metric-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(150px, 1fr)); gap: 12px; margin-bottom: 20px; }
.metric { display: grid; gap: 5px; padding: 15px; border-radius: 14px; background: #f1f6f1; }
.metric span { color: #5d6c64; font-size: 13px; } .metric strong { font-size: 28px; } .metric.warning { background: #fff4e4; }
.workspace-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
.card { min-width: 0; padding: 20px; border: 1px solid #dce6dd; border-radius: 16px; background: #fff; }
.card.wide { grid-column: span 2; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; margin: 18px 0; }
label { display: grid; gap: 6px; color: #5d6c64; font-size: 12px; font-weight: 700; }
.span-2 { grid-column: span 2; }
input, select, textarea { width: 100%; padding: 9px 10px; border: 1px solid #cbd9cd; border-radius: 8px; color: #17231d; background: #fbfdfb; font: inherit; font-size: 13px; }
textarea { resize: vertical; }
button { padding: 9px 13px; border: 0; border-radius: 9px; color: #fff; background: #217a55; font-weight: 700; cursor: pointer; }
button:disabled { cursor: not-allowed; opacity: .45; } button.secondary { color: #217a55; border: 1px solid #bcd0c0; background: #fff; }
.actions { display: flex; flex-wrap: wrap; gap: 8px; }
.state { margin: 10px 0; padding: 10px 12px; border-radius: 9px; color: #5d6c64; background: #f1f6f1; }
.state.error { color: #8c2525; background: #feecec; } .state.success { color: #17623f; background: #e1f4e8; }
.compact-list, .record-list { display: grid; gap: 8px; padding: 0; margin: 18px 0 0; list-style: none; }
.compact-list li, .record-list li { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 10px 0; border-top: 1px solid #edf2ed; }
.compact-list li > *, .record-list li > * { min-width: 0; } .compact-list strong, .record-action { margin-left: auto; text-align: right; }
.record-list li > div:first-child { display: grid; gap: 4px; } .record-action { display: flex; align-items: center; gap: 9px; }
.record-action em { color: #217a55; font-size: 11px; font-style: normal; font-weight: 800; }
.link-button { padding: 0; color: #217a55; background: transparent; font-size: 12px; } .danger { color: #a34129; }
@media (max-width: 760px) { .governance { padding: 18px; } .workspace-grid { grid-template-columns: 1fr; } .card.wide { grid-column: auto; } .span-2 { grid-column: auto; } .section-heading { display: grid; } }
</style>
