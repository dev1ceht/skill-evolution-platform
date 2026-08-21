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

    await wrapper.get('[aria-label="取消订单"]').trigger('click');
    await flushPromises();

    expect(cancelMealOrder).toHaveBeenCalledWith('MEAL-001', scope);
    expect(wrapper.text()).toContain('订单 MO-001 已取消');
  });

  it('completes a study mock payment for an own unpaid order and refreshes the list', async () => {
    const listMealOrders = vi.fn()
      .mockResolvedValueOnce([order])
      .mockResolvedValueOnce([{ ...order, paymentStatus: 'PAID' as const }]);
    const payMealOrder = vi.fn().mockResolvedValue({ ...order, paymentStatus: 'PAID' as const });
    const api: SmartCanteenApiPort = {
      listDinerMenus: vi.fn().mockResolvedValue([menu]),
      listMealOrders,
      payMealOrder,
    };
    const wrapper = mount(DinerWorkspace, { props: { api, scope } });
    await flushPromises();

    await wrapper.get('[aria-label="模拟支付"]').trigger('click');
    await flushPromises();

    expect(payMealOrder).toHaveBeenCalledWith('MEAL-001', expect.stringContaining('diner-payment-'), scope);
    expect(wrapper.text()).toContain('订单 MO-001 已完成学习环境模拟支付');
    expect(wrapper.text()).toContain('已支付');
  });

  it('refreshes the order list when study mock payment reports an error', async () => {
    const listMealOrders = vi.fn()
      .mockResolvedValueOnce([order])
      .mockResolvedValueOnce([{ ...order, paymentStatus: 'PAID' as const }]);
    const payMealOrder = vi.fn().mockRejectedValue(new Error('支付服务暂不可用'));
    const api: SmartCanteenApiPort = {
      listDinerMenus: vi.fn().mockResolvedValue([menu]),
      listMealOrders,
      payMealOrder,
    };
    const wrapper = mount(DinerWorkspace, { props: { api, scope } });
    await flushPromises();

    await wrapper.get('[aria-label="模拟支付"]').trigger('click');
    await flushPromises();

    expect(payMealOrder).toHaveBeenCalledTimes(1);
    expect(listMealOrders).toHaveBeenCalledTimes(2);
    expect(wrapper.text()).toContain('支付服务暂不可用');
    expect(wrapper.text()).toContain('已支付');
  });

  it('submits a review for an own active order and reloads personal reviews', async () => {
    const review = {
      id: 'REVIEW-001',
      actorUserId: 'USER-001',
      orderId: 'MEAL-001',
      orderNo: 'MO-001',
      rating: 5,
      content: '很好吃',
      status: 'SUBMITTED' as const,
      version: 0,
      createdAt: '2026-08-21T03:00:00Z',
      updatedAt: '2026-08-21T03:00:00Z',
    };
    const listMealReviews = vi.fn().mockResolvedValueOnce([]).mockResolvedValueOnce([review]);
    const createMealReview = vi.fn().mockResolvedValue(review);
    const api: SmartCanteenApiPort = {
      listDinerMenus: vi.fn().mockResolvedValue([menu]),
      listMealOrders: vi.fn().mockResolvedValue([order]),
      listMealReviews,
      createMealReview,
    };
    const wrapper = mount(DinerWorkspace, { props: { api, scope } });
    await flushPromises();

    await wrapper.get('textarea[placeholder*="用餐体验"]').setValue('很好吃');
    await wrapper.get('[aria-label="提交评价"]').trigger('click');
    await flushPromises();

    expect(createMealReview).toHaveBeenCalledWith(
      { orderId: 'MEAL-001', rating: 5, content: '很好吃' },
      expect.stringContaining('diner-review-'),
      scope,
    );
    expect(wrapper.text()).toContain('订单 MO-001 的评价已提交');
    expect(wrapper.text()).toContain('很好吃');
  });

  it('submits an employee complaint and reloads personal complaints', async () => {
    const complaint = {
      id: 'COMPLAINT-001',
      actorUserId: 'USER-001',
      category: 'SERVICE' as const,
      subject: '窗口服务',
      description: '排队时间较长',
      relatedOrderId: null,
      status: 'SUBMITTED' as const,
      reply: null,
      version: 0,
      createdAt: '2026-08-21T03:00:00Z',
      updatedAt: '2026-08-21T03:00:00Z',
    };
    const listDinerComplaints = vi.fn().mockResolvedValueOnce([]).mockResolvedValueOnce([complaint]);
    const createDinerComplaint = vi.fn().mockResolvedValue(complaint);
    const api: SmartCanteenApiPort = {
      listDinerMenus: vi.fn().mockResolvedValue([menu]),
      listMealOrders: vi.fn().mockResolvedValue([]),
      listDinerComplaints,
      createDinerComplaint,
    };
    const wrapper = mount(DinerWorkspace, { props: { api, scope } });
    await flushPromises();

    await wrapper.get('input[placeholder="例如：窗口服务"]').setValue('窗口服务');
    await wrapper.get('textarea[placeholder="请描述遇到的问题"]').setValue('排队时间较长');
    await wrapper.get('form.complaint-form').trigger('submit');
    await flushPromises();

    expect(createDinerComplaint).toHaveBeenCalledWith(
      {
        category: 'SERVICE',
        subject: '窗口服务',
        description: '排队时间较长',
        relatedOrderId: undefined,
      },
      expect.stringContaining('diner-complaint-'),
      scope,
    );
    expect(wrapper.text()).toContain('投诉已提交');
    expect(wrapper.text()).toContain('排队时间较长');
  });
});
