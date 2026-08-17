import { flushPromises, mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import WorkflowDashboard from '../src/components/WorkflowDashboard.vue';
import type { SmartCanteenApiPort } from '../src/api/smartCanteenApi';

function apiStub(overrides: Partial<SmartCanteenApiPort> = {}): SmartCanteenApiPort {
  return {
    getCurrentLedgerAlert: vi.fn().mockResolvedValue({
      cleared: false,
      missingLedgerCodes: ['PURCHASE_ACCEPTANCE'],
    }),
    submitMenu: vi.fn().mockResolvedValue({ id: 'MENU-001', status: 'PENDING_APPROVAL' }),
    importMenuRecipe: vi.fn().mockResolvedValue({ menuId: 'MENU-001', requirements: [] }),
    decideMenuApproval: vi.fn().mockResolvedValue({ id: 'MENU-001', status: 'APPROVED' }),
    generateProcurementPlan: vi.fn().mockResolvedValue({ menuId: 'MENU-001', items: [] }),
    receiveInventory: vi.fn().mockResolvedValue({
      materialId: 'FLOUR', quantityBase: 2000, baseUnit: 'g',
    }),
    completeLedgerRecord: vi.fn().mockResolvedValue({ cleared: true, missingLedgerCodes: [] }),
    ...overrides,
  };
}

describe('WorkflowDashboard', () => {
  it('renders loading and empty alert states', async () => {
    let resolveAlert!: (value: { cleared: boolean; missingLedgerCodes: string[] }) => void;
    const api = apiStub({
      getCurrentLedgerAlert: vi.fn().mockReturnValue(
        new Promise((resolve) => { resolveAlert = resolve; }),
      ),
    });
    const wrapper = mount(WorkflowDashboard, { props: { api } });

    expect(wrapper.get('[data-testid="loading"]').text()).toContain('加载');
    resolveAlert({ cleared: true, missingLedgerCodes: [] });
    await flushPromises();

    expect(wrapper.get('[data-testid="empty-alert"]').text()).toContain('无待补台账');
  });

  it('renders an error state when the initial request fails', async () => {
    const api = apiStub({
      getCurrentLedgerAlert: vi.fn().mockRejectedValue(new Error('网络不可用')),
    });

    const wrapper = mount(WorkflowDashboard, { props: { api } });
    await flushPromises();

    expect(wrapper.get('[data-testid="error"]').text()).toContain('网络不可用');
  });

  it('submits and approves a menu through explicit user interactions', async () => {
    const api = apiStub();
    const wrapper = mount(WorkflowDashboard, { props: { api } });
    await flushPromises();

    await wrapper.get('[data-testid="submit-menu"]').trigger('click');
    await flushPromises();
    expect(wrapper.get('[data-testid="menu-status"]').text()).toContain('PENDING_APPROVAL');

    await wrapper.get('[data-testid="approve-menu"]').trigger('click');
    await flushPromises();
    expect(api.decideMenuApproval).toHaveBeenCalledWith('MENU-001', 'APPROVE', '页面审批通过');
    expect(wrapper.get('[data-testid="menu-status"]').text()).toContain('APPROVED');
  });
});
