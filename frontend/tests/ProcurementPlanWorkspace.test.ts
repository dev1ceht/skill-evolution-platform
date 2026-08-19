import { flushPromises, mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import ProcurementPlanWorkspace from '../src/components/ProcurementPlanWorkspace.vue';
import type { SmartCanteenApiPort } from '../src/api/smartCanteenApi';

const scope = { schoolId: 'SCHOOL-001', canteenId: 'CANTEEN-001' };

function plan(status = 'DRAFT', version = 0) {
  return {
    id: 'PLAN-001',
    planNo: 'PLAN-001',
    periodStart: '2026-08-14',
    periodEnd: '2026-08-14',
    status,
    version,
    sourceMenuIds: ['M001'],
    items: [{
      ingredientId: 'RICE',
      requiredBaseQuantity: 10000,
      inventoryBaseQuantity: 1000,
      openOrderBaseQuantity: 0,
      shortageBaseQuantity: 9000,
      plannedBaseQuantity: 9000,
      baseUnit: 'g',
    }],
    orderIds: [],
  };
}

function apiStub(overrides: Partial<SmartCanteenApiPort> = {}): SmartCanteenApiPort {
  return {
    listProcurementPlans: vi.fn().mockResolvedValue([plan()]),
    listSuppliers: vi.fn().mockResolvedValue([
      { id: 'SUPPLIER-001', name: '本地供应商', active: true },
    ]),
    adjustProcurementPlan: vi.fn().mockResolvedValue(plan('DRAFT', 1)),
    confirmProcurementPlan: vi.fn().mockResolvedValue(plan('CONFIRMED', 2)),
    createPurchaseOrderFromPlan: vi.fn().mockResolvedValue({
      id: 'ORDER-001',
      orderNo: 'PO-001',
      supplierId: 'SUPPLIER-001',
      orderType: 'OFFLINE',
      status: 'DRAFT',
      totalAmount: 0,
      items: [],
    }),
    ...overrides,
  };
}

describe('ProcurementPlanWorkspace', () => {
  it('loads a plan and completes adjust, confirm and order actions', async () => {
    const api = apiStub();
    const wrapper = mount(ProcurementPlanWorkspace, { props: { api, scope } });
    await flushPromises();

    expect(wrapper.get('[data-testid="procurement-plan-workspace"]').text()).toContain('PLAN-001');
    expect(wrapper.get('[data-testid="procurement-plan-adjust"]').attributes('disabled')).toBeUndefined();

    await wrapper.get('[data-testid="procurement-plan-adjust"]').trigger('click');
    await flushPromises();
    expect(api.adjustProcurementPlan).toHaveBeenCalledWith(
      'PLAN-001',
      { version: 0, items: [{ ingredientId: 'RICE', quantity: 9000, unit: 'g' }] },
      scope,
    );

    await wrapper.get('[data-testid="procurement-plan-confirm"]').trigger('click');
    await flushPromises();
    expect(api.confirmProcurementPlan).toHaveBeenCalledWith('PLAN-001', scope);
    expect(wrapper.find('[data-testid="procurement-order-create"]').exists()).toBe(true);

    await wrapper.get('[data-testid="procurement-order-create"]').trigger('click');
    await flushPromises();
    expect(api.createPurchaseOrderFromPlan).toHaveBeenCalledWith(
      'PLAN-001',
      'procurement-order:PLAN-001',
      {
        supplierId: 'SUPPLIER-001',
        orderType: 'OFFLINE',
        items: [{ ingredientId: 'RICE', quantity: 9000, unit: 'g', unitPrice: 0 }],
      },
      scope,
    );
    expect(wrapper.get('[data-testid="procurement-plan-notice"]').text()).toContain('采购单 PO-001');
  });

  it('renders a recoverable loading error', async () => {
    const api = apiStub({
      listProcurementPlans: vi.fn().mockRejectedValue(new Error('计划服务不可用')),
    });
    const wrapper = mount(ProcurementPlanWorkspace, { props: { api, scope } });
    await flushPromises();

    expect(wrapper.get('[data-testid="procurement-plan-error"]').text()).toContain('计划服务不可用');
  });

  it('can cancel a draft plan through the lifecycle endpoint', async () => {
    const api = apiStub({
      cancelProcurementPlan: vi.fn().mockResolvedValue(plan('CANCELLED', 1)),
    });
    const wrapper = mount(ProcurementPlanWorkspace, { props: { api, scope } });
    await flushPromises();

    await wrapper.get('[data-testid="procurement-plan-cancel"]').trigger('click');
    await flushPromises();

    expect(api.cancelProcurementPlan).toHaveBeenCalledWith('PLAN-001', scope);
    expect(wrapper.text()).toContain('CANCELLED');
  });
});
