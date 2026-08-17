import { flushPromises, mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import AgentMetricsDashboard from '../src/components/AgentMetricsDashboard.vue';
import type { SmartCanteenApiPort } from '../src/api/smartCanteenApi';

const scope = { schoolId: 'SCHOOL-METRICS', canteenId: 'CANTEEN-METRICS' };

function metrics(totalRuns = 2) {
  return {
    schoolId: scope.schoolId,
    canteenId: scope.canteenId,
    from: '2026-08-16T00:00:00Z',
    to: '2026-08-17T00:00:00Z',
    totalRuns,
    succeededRuns: totalRuns,
    failedRuns: 0,
    rejectedRuns: 0,
    cancelledRuns: 0,
    timedOutRuns: 0,
    reconciliationRequiredRuns: 0,
    waitingConfirmationRuns: 0,
    successRate: totalRuns === 0 ? 0 : 1,
    averageRunDurationMs: 1250,
    averageConfirmationWaitMs: 0,
    toolExecutions: totalRuns,
    toolFailures: 0,
    averageToolDurationMs: 600,
    idempotencyReplayCount: 1,
    authorizationDeniedCount: 0,
  };
}

function apiStub(getAgentMetrics: SmartCanteenApiPort['getAgentMetrics']): SmartCanteenApiPort {
  return { getAgentMetrics } as SmartCanteenApiPort;
}

describe('AgentMetricsDashboard', () => {
  it('renders loading and aggregate success states', async () => {
    let resolve!: (value: ReturnType<typeof metrics>) => void;
    const getAgentMetrics = vi.fn().mockReturnValue(new Promise((done) => { resolve = done; }));
    const wrapper = mount(AgentMetricsDashboard, {
      props: { api: apiStub(getAgentMetrics), scope },
    });

    expect(getAgentMetrics).toHaveBeenCalledTimes(1);
    await wrapper.vm.$nextTick();
    expect(wrapper.find('[data-testid="agent-metrics-loading"]').exists()).toBe(true);
    resolve(metrics());
    await flushPromises();

    expect(wrapper.get('[data-testid="agent-metrics-total"]').text()).toBe('2');
    expect(wrapper.get('[data-testid="agent-metrics-content"]').text()).toContain('成功率 100.0%');
    expect(getAgentMetrics).toHaveBeenCalledTimes(1);
  });

  it('renders an empty state when the scope has no runs', async () => {
    const wrapper = mount(AgentMetricsDashboard, {
      props: { api: apiStub(vi.fn().mockResolvedValue(metrics(0))), scope },
    });
    await flushPromises();

    expect(wrapper.get('[data-testid="agent-metrics-empty"]').text()).toContain('暂无');
  });

  it('renders a recoverable error state', async () => {
    const wrapper = mount(AgentMetricsDashboard, {
      props: { api: apiStub(vi.fn().mockRejectedValue(new Error('指标服务不可用'))), scope },
    });
    await flushPromises();

    expect(wrapper.get('[data-testid="agent-metrics-error"]').text()).toContain('指标服务不可用');
  });
});
