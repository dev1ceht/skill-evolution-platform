const state = { integration: null, candidate: null, evaluation: null, version: null, tab: "tasks" };
const $ = (selector) => document.querySelector(selector);
const toast = (message) => { const el = $("#toast"); el.textContent = message; el.classList.add("show"); setTimeout(() => el.classList.remove("show"), 2400); };

async function api(path, options = {}) {
  const response = await fetch(path, { headers: { "Content-Type": "application/json" }, ...options });
  const payload = await response.json();
  if (!response.ok) throw new Error(payload.error || `HTTP ${response.status}`);
  return payload;
}

async function loadContract() {
  const contract = await fetch("/examples/user-api.openapi.json").then((response) => response.json());
  $("#contractInput").value = JSON.stringify(contract, null, 2);
}

async function loadDashboard() {
  const dashboard = await api("/api/dashboard");
  Object.entries(dashboard.metrics).forEach(([key, value]) => {
    const node = document.querySelector(`[data-metric="${key}"]`);
    if (node) node.textContent = value;
  });
  $("#auditList").innerHTML = dashboard.recentAudit.length ? dashboard.recentAudit.slice().reverse().map((event) => `
    <div class="event"><code>${event.action}</code><span>${event.entity_type} · ${event.entity_id}</span><time>${new Date(event.created_at).toLocaleTimeString()}</time></div>`).join("") : '<p class="empty">暂无事件，运行上方 Demo 后将在这里形成审计轨迹。</p>';
}

function renderIntegration() {
  if (!state.integration) return;
  const value = state.tab === "tasks" ? state.integration.tasks : state.tab === "ir" ? { title: state.integration.title, version: state.integration.version, operations: state.integration.operations } : state.integration.typescriptClient;
  $("#integrationOutput").textContent = typeof value === "string" ? value : JSON.stringify(value, null, 2);
}

async function runIntegration() {
  $("#integrationStatus").textContent = "RUNNING";
  try {
    const document = JSON.parse($("#contractInput").value);
    state.integration = await api("/api/integrations", { method: "POST", body: JSON.stringify({ document, pageName: "UserListPage" }) });
    $("#integrationStatus").textContent = `${state.integration.operations.length} OPS · DONE`;
    renderIntegration(); await loadDashboard(); toast("API IR、任务计划与 TypeScript client 已生成");
  } catch (error) { $("#integrationStatus").textContent = "FAILED"; toast(error.message); }
}

async function runEvolution() {
  try {
    const episode = await api("/api/episodes", { method: "POST", body: JSON.stringify({ task: "生成用户列表页接口对接", skillName: "frontend-api-integration", skillVersion: "1.0.0", outputSummary: "生成了 pageNo 分页逻辑" }) });
    state.candidate = await api(`/api/episodes/${episode.id}/feedback`, { method: "POST", body: JSON.stringify({ feedback: $("#feedbackText").textContent.replace(/[“”]/g, "") }) });
    $("#episodeId").textContent = state.candidate.sourceEpisodeId; $("#similarity").textContent = `${Math.round(state.candidate.similarity * 100)}%`;
    $("#candidateDecision").textContent = state.candidate.decision.toUpperCase(); $("#candidateLight").textContent = "✓"; $("#candidateLight").className = "ok"; $("#decisionLight").textContent = state.candidate.decision.toUpperCase(); $("#decisionLight").className = "ok";
    $("#evaluateCandidate").disabled = state.candidate.status !== "staged"; await loadDashboard(); toast("反馈已归因并生成 staged candidate");
  } catch (error) { toast(error.message); }
}

async function evaluateCandidate() {
  try {
    state.evaluation = await api(`/api/candidates/${state.candidate.id}/evaluate`, { method: "POST", body: "{}" });
    const checks = state.evaluation.checks; const passed = Object.values(checks).filter(Boolean).length; const score = Math.round(passed / Object.keys(checks).length * 100);
    $("#qualityScore").textContent = score; $(".score-line i").style.width = `${score}%`; $("#checkFrontmatter").textContent = checks.frontmatterPreserved ? "PASS" : "FAIL"; $("#checkRule").textContent = checks.candidateRulePresent ? "PASS" : "FAIL"; $("#checkSize").textContent = checks.skillSizeWithinLimit ? "PASS" : "FAIL"; $("#checkTodo").textContent = checks.noTodoPlaceholder ? "PASS" : "FAIL";
    $("#judgeLight").textContent = state.evaluation.passed ? "PASS" : "FAIL"; $("#judgeLight").className = state.evaluation.passed ? "ok" : ""; $("#gateResult").textContent = `${state.evaluation.judge} · replay ${state.evaluation.replayCaseId} · ${state.evaluation.passed ? "允许晋级" : "禁止晋级"}`; $("#promoteCandidate").disabled = !state.evaluation.passed; await loadDashboard(); toast("Replay 与离线质量门禁执行完成");
  } catch (error) { toast(error.message); }
}

async function promoteCandidate() {
  try { state.version = await api(`/api/candidates/${state.candidate.id}/promote`, { method: "POST", body: "{}" }); $("#promoteLight").textContent = state.version.version; $("#promoteLight").className = "ok"; $("#promoteCandidate").disabled = true; await loadDashboard(); toast(`Skill ${state.version.version} 已原子落盘，可回滚`); } catch (error) { toast(error.message); }
}

document.querySelectorAll(".tab").forEach((tab) => tab.addEventListener("click", () => { document.querySelectorAll(".tab").forEach((node) => node.classList.remove("active")); tab.classList.add("active"); state.tab = tab.dataset.tab; renderIntegration(); }));
$("#runIntegration").addEventListener("click", runIntegration); $("#runEvolution").addEventListener("click", runEvolution); $("#evaluateCandidate").addEventListener("click", evaluateCandidate); $("#promoteCandidate").addEventListener("click", promoteCandidate); $("#refresh").addEventListener("click", loadDashboard);
Promise.all([loadContract(), loadDashboard()]).catch((error) => toast(error.message));
