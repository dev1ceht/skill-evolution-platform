import { flushPromises, mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import DinerWorkspace from '../src/components/DinerWorkspace.vue';
import type { SmartCanteenApiPort } from '../src/api/smartCanteenApi';

const scope = { schoolId: 'SCHOOL-001', canteenId: 'CANTEEN-001' };

const menu = {
  id: 'M001',
  menuDate: '2026-08-21',
  mealTime: 'LUNCH',
  items: [{
    dishId: 'DISH-001',
    name: '番茄鸡蛋',
    category: '热菜',
    description: '家常口味',
    imageUrl: null,
  }],
};

const order = {
  id: 'MEAL-001',
  orderNo: 'MO-001',
  actorUserId: 'USER-001',
  menuId: 'M001',
  mealDate: '2026-08-21',
  mealTime: 'LUNCH',
  status: 'CREATED' as const,
  paymentStatus: 'UNPAID' as const,
  totalAmount: 0,
  items: [{ dishId: 'DISH-001', dishName: '番茄鸡蛋', quantity: 1, unitPrice: 0, amount: 0 }],
  version: 0,
  createdAt: '2026-08-21T03:00:00Z',
  updatedAt: '2026-08-21T03:00:00Z',
};

describe('DinerWorkspace', () => {
  it('loads a published menu and creates a personal unpaid order', async () => {
    const createMealOrder = vi.fn().mockResolvedValue({ ...order, id: 'MEAL-002', orderNo: 'MO-002' });
    const listMealOrders = vi.fn().mockResolvedValue([]);
    const api: SmartCanteenApiPort = {
      listDinerMenus: vi.fn().mockResolvedValue([menu]),
      listMealOrders,
      createMealOrder,
    };
    const wrapper = mount(DinerWorkspace, { props: { api, scope } });
    await flushPromises();

    expect(wrapper.text()).toContain('番茄鸡蛋');
    await wrapper.get('[aria-label="增加数量"]').trigger('click');
    await wrapper.get('button.primary').trigger('click');
    await flushPromises();

    expect(createMealOrder).toHaveBeenCalledWith(
      { menuId: 'M001', items: [{ dishId: 'DISH-001', quantity: 1 }] },
      expect.stringContaining('diner-order-'),
      scope,
    );
    expect(wrapper.text()).toContain('订单 MO-002 已创建');
  });

  it('cancels only a created personal order and refreshes the list', async () => {
    const listMealOrders = vi.fn()
      .mockResolvedValueOnce([order])
      .mockResolvedValueOnce([{ ...order, status: 'CANCELLED' as const }]);
    const cancelMealOrder = vi.fn().mockResolvedValue({ ...order, status: 'CANCELLED' as const });
    const api: SmartCanteenApiPort = {
      listDinerMenus: vi.fn().mockResolvedValue([menu]),
      listMealOrders,
      cancelMealOrder,
    };
    const wrapper = mount(DinerWorkspace, { props: { api, scope } });
    await flushPromises();

    await wrapper.get('.order-bottom button').trigger('click');
    await flushPromises();

    expect(cancelMealOrder).toHaveBeenCalledWith('MEAL-001', scope);
    expect(wrapper.text()).toContain('订单 MO-001 已取消');
  });
});
