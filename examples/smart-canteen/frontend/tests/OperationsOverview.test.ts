import { flushPromises, mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import OperationsOverview from '../src/components/OperationsOverview.vue';
import type { SmartCanteenApiPort } from '../src/api/smartCanteenApi';

const scope = { schoolId: 'SCHOOL-001', canteenId: 'CANTEEN-001' };

function summary() {
  return {
    date: '2026-08-14',
    todayMenuCount: 3,
    publishedMenuCount: 2,
    pendingPurchaseOrderCount: 1,
    inventoryWarningCount: 2,
    openLedgerAlertCount: 1,
    openExternalAlertCount: 0,
    purchaseAmount: 128.5,
  };
}

describe('OperationsOverview', () => {
  it('renders loading and live summary states', async () => {
    let resolveSummary!: (value: ReturnType<typeof summary>) => void;
    const api: SmartCanteenApiPort = {
      getCurrentLedgerAlert: vi.fn(),
      submitMenu: vi.fn(),
      importMenuRecipe: vi.fn(),
      decideMenuApproval: vi.fn(),
      generateProcurementPlan: vi.fn(),
      receiveInventory: vi.fn(),
      completeLedgerRecord: vi.fn(),
      getDashboardSummary: vi.fn().mockReturnValue(
        new Promise((resolve) => { resolveSummary = resolve; }),
      ),
    };
    const wrapper = mount(OperationsOverview, { props: { api, scope } });

    expect(wrapper.find('[data-testid="overview-loading"]').exists()).toBe(true);
    resolveSummary(summary());
    await flushPromises();

    expect(wrapper.get('[data-testid="overview-summary"]').text()).toContain('128.50');
    expect(wrapper.get('[data-testid="overview-summary"]').text()).toContain('库存预警');
  });

  it('renders a recoverable error state', async () => {
    const api: SmartCanteenApiPort = {
      getCurrentLedgerAlert: vi.fn(),
      submitMenu: vi.fn(),
      importMenuRecipe: vi.fn(),
      decideMenuApproval: vi.fn(),
      generateProcurementPlan: vi.fn(),
      receiveInventory: vi.fn(),
      completeLedgerRecord: vi.fn(),
      getDashboardSummary: vi.fn().mockRejectedValue(new Error('服务不可用')),
    };
    const wrapper = mount(OperationsOverview, { props: { api, scope } });
    await flushPromises();

    expect(wrapper.get('[data-testid="overview-error"]').text()).toContain('服务不可用');
  });
});
