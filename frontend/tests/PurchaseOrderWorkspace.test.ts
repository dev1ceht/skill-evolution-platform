import { flushPromises, mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import PurchaseOrderWorkspace from '../src/components/PurchaseOrderWorkspace.vue';
import type { SmartCanteenApiPort } from '../src/api/smartCanteenApi';

const scope = { schoolId: 'SCHOOL-001', canteenId: 'CANTEEN-001' };

function order(status: string) {
  return {
    id: 'ORDER-001',
    orderNo: 'PO-001',
    supplierId: 'SUPPLIER-001',
    orderType: 'OFFLINE',
    status,
    totalAmount: 40,
    items: [{ ingredientId: 'RICE', quantity: 2, unit: 'bag', unitPrice: 20, amount: 40 }],
  };
}

function apiStub(overrides: Partial<SmartCanteenApiPort> = {}): SmartCanteenApiPort {
  return {
    listPurchaseOrders: vi.fn().mockResolvedValue([order('DRAFT')]),
    transitionPurchaseOrder: vi.fn().mockResolvedValue(order('SUBMITTED')),
    receivePurchaseOrder: vi.fn().mockResolvedValue({
      orderId: 'ORDER-001',
      receiptId: 'RECEIPT-001',
      traceCodes: ['TRACE-001'],
    }),
    ...overrides,
  };
}

describe('PurchaseOrderWorkspace', () => {
  it('advances a draft order to supplier submission', async () => {
    const api = apiStub();
    const wrapper = mount(PurchaseOrderWorkspace, { props: { api, scope } });
    await flushPromises();

    await wrapper.get('button:not(.secondary)').trigger('click');
    await flushPromises();

    expect(api.transitionPurchaseOrder).toHaveBeenCalledWith('ORDER-001', 'SUBMITTED', scope);
    expect(wrapper.get('[data-testid="purchase-order-notice"]').text()).toContain('SUBMITTED');
  });

  it('receives the remaining confirmed order quantity idempotently', async () => {
    const api = apiStub({
      listPurchaseOrders: vi.fn().mockResolvedValue([order('CONFIRMED')]),
    });
    const wrapper = mount(PurchaseOrderWorkspace, { props: { api, scope } });
    await flushPromises();

    await wrapper.get('[data-testid="purchase-order-receive"]').trigger('click');
    await flushPromises();

    expect(api.receivePurchaseOrder).toHaveBeenCalledWith(
      'ORDER-001',
      'receipt:ORDER-001:remaining',
      { items: [] },
      scope,
    );
    expect(wrapper.get('[data-testid="purchase-order-notice"]').text()).toContain('TRACE-001');
  });
});
