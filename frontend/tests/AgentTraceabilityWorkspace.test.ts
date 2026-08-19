import { flushPromises, mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import AgentTraceabilityWorkspace from '../src/components/AgentTraceabilityWorkspace.vue';
import type { AgentRun } from '../src/api/generated/client';
import type { SmartCanteenApiPort } from '../src/api/smartCanteenApi';

const scope = { schoolId: 'SCHOOL-001', canteenId: 'CANTEEN-001' };

function run(status = 'SUCCEEDED'): AgentRun {
  return {
    runId: 'RUN-001',
    version: 0,
    status,
    intent: 'traceability.query',
    skillId: 'smart-canteen.traceability',
    skillVersion: '1.0.0',
    manifestDigest: 'a'.repeat(64),
    planHash: 'b'.repeat(64),
    plan: {},
    result: {
      traceCode: 'TRACE-001',
      ingredientName: '青菜',
      batchId: 'BATCH-001',
      supplierName: '供应商 A',
    },
    createdAt: '2026-08-16T10:00:00Z',
    updatedAt: '2026-08-16T10:00:01Z',
  };
}

function apiWith(startAgentTraceability: SmartCanteenApiPort['startAgentTraceability']): SmartCanteenApiPort {
  return {
    startAgentTraceability,
  };
}

describe('AgentTraceabilityWorkspace', () => {
  it('renders a successful read-only Run result', async () => {
    const start = vi.fn().mockResolvedValue(run());
    const wrapper = mount(AgentTraceabilityWorkspace, {
      props: { api: apiWith(start), scope },
    });

    await wrapper.get('input').setValue('TRACE-001');
    await wrapper.get('form').trigger('submit');
    await flushPromises();

    expect(start).toHaveBeenCalledWith(
      'TRACE-001',
      scope,
      expect.stringContaining('agent-trace-TRACE-001-'),
    );
    expect(wrapper.get('[data-testid="agent-trace-result"]').text()).toContain('供应商 A');
    expect(wrapper.text()).toContain('SUCCEEDED');
  });

  it('shows a recoverable business error', async () => {
    const start = vi.fn().mockRejectedValue(new Error('溯源码不存在'));
    const wrapper = mount(AgentTraceabilityWorkspace, {
      props: { api: apiWith(start), scope },
    });

    await wrapper.get('input').setValue('TRACE-MISSING');
    await wrapper.get('form').trigger('submit');
    await flushPromises();

    expect(wrapper.get('[data-testid="agent-trace-error"]').text()).toContain('溯源码不存在');
  });
});
