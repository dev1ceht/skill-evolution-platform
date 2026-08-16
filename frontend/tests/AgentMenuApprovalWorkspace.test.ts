import { flushPromises, mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import AgentMenuApprovalWorkspace from '../src/components/AgentMenuApprovalWorkspace.vue';
import type { AgentRun } from '../src/api/generated/client';
import type { SmartCanteenApiPort } from '../src/api/smartCanteenApi';

const scope = { schoolId: 'SCHOOL-001', canteenId: 'CANTEEN-001' };

function run(status: string, version = 0): AgentRun {
  return {
    runId: 'RUN-MENU-001',
    version,
    status,
    intent: 'menu.submit',
    skillId: 'smart-canteen.menu-approval',
    skillVersion: '1.0.0',
    manifestDigest: 'a'.repeat(64),
    planHash: 'b'.repeat(64),
    plan: {
      tools: ['menu.submit'],
      businessParameters: { menuId: 'MENU-001', menuVersion: 0 },
    },
    createdAt: '2026-08-16T10:00:00Z',
    updatedAt: '2026-08-16T10:00:01Z',
  };
}

function apiWith(overrides: Partial<SmartCanteenApiPort>): SmartCanteenApiPort {
  return {
    getCurrentLedgerAlert: vi.fn(),
    submitMenu: vi.fn(),
    importMenuRecipe: vi.fn(),
    decideMenuApproval: vi.fn(),
    generateProcurementPlan: vi.fn(),
    receiveInventory: vi.fn(),
    completeLedgerRecord: vi.fn(),
    ...overrides,
  };
}

describe('AgentMenuApprovalWorkspace', () => {
  it('shows a run confirmation and sends the expected version when confirming', async () => {
    const startAgentRun = vi.fn().mockResolvedValue(run('WAITING_CONFIRMATION'));
    const decideAgentRun = vi.fn().mockResolvedValue(run('SUCCEEDED', 1));
    const getAgentRunEvents = vi.fn().mockResolvedValue([
      {
        eventId: 'EVENT-1', runId: 'RUN-MENU-001', eventSequence: 1,
        eventType: 'RUN_PLANNED', toStatus: 'WAITING_CONFIRMATION', occurredAt: '2026-08-16T10:00:00Z',
      },
    ]);
    const getDailyMenu = vi.fn().mockResolvedValue({
      id: 'MENU-001', menuDate: '2026-08-17', mealTime: 'LUNCH', status: 'DRAFT', version: 0,
      items: [{ dishId: 'DISH-001', estimatedQuantity: 1, sortOrder: 0 }],
    });
    const wrapper = mount(AgentMenuApprovalWorkspace, {
      props: { api: apiWith({ startAgentRun, decideAgentRun, getAgentRunEvents, getDailyMenu }), scope },
    });

    await wrapper.get('#agent-menu-id').setValue('MENU-001');
    await wrapper.get('form').trigger('submit');
    await flushPromises();

    expect(startAgentRun).toHaveBeenCalledWith(
      'menu.submit', { menuId: 'MENU-001', menuVersion: 0 }, scope,
      expect.stringContaining('agent-menu-menu.submit-MENU-001-'),
    );
    expect(getDailyMenu).toHaveBeenCalledWith('MENU-001', scope);
    expect(wrapper.get('[data-testid="agent-menu-run"]').text()).toContain('WAITING_CONFIRMATION');
    expect(wrapper.get('[data-testid="agent-menu-domain-state"]').text())
      .toContain('当前状态：DRAFT · 下一步：可提交领域审批');
    expect(wrapper.get('[data-testid="agent-menu-plan-summary"]').text())
      .toContain('目标菜单 MENU-001 · 菜单版本 0');
    expect(wrapper.get('#agent-menu-id').attributes('disabled')).toBeDefined();

    await wrapper.get('button:not([type="submit"])').trigger('click');
    await flushPromises();

    expect(decideAgentRun).toHaveBeenCalledWith(
      'RUN-MENU-001', 'RUN_CONFIRM', 0, scope, undefined, undefined,
      expect.stringContaining('agent-menu-decision-RUN-MENU-001-0-RUN_CONFIRM'),
    );
    expect(wrapper.text()).toContain('SUCCEEDED');
  });
});
