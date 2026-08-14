import type { AxiosInstance } from 'axios';
import { describe, expect, it, vi } from 'vitest';
import { SmartCanteenApi } from '../src/api/smartCanteenApi';

describe('SmartCanteenApi', () => {
  it('encodes path parameters and unwraps code-message-data responses', async () => {
    const post = vi.fn().mockResolvedValue({
      data: {
        code: 0,
        message: 'success',
        data: { id: 'MENU/001', status: 'APPROVED', decisionComment: '通过' },
      },
    });
    const api = new SmartCanteenApi({ post } as unknown as AxiosInstance);

    const menu = await api.decideMenuApproval('MENU/001', 'APPROVE', '通过');

    expect(post).toHaveBeenCalledWith('/api/v1/menu-approvals/MENU%2F001/decision', {
      decision: 'APPROVE',
      comment: '通过',
    });
    expect(menu.status).toBe('APPROVED');
  });

  it('turns a non-zero business code into a typed error', async () => {
    const get = vi.fn().mockResolvedValue({
      data: { code: 40001, message: '台账查询失败', data: null },
    });
    const api = new SmartCanteenApi({ get } as unknown as AxiosInstance);

    await expect(api.getCurrentLedgerAlert()).rejects.toMatchObject({
      name: 'ApiBusinessError',
      code: 40001,
      message: '台账查询失败',
    });
  });

  it('passes an explicit school and canteen scope without changing the endpoint path', async () => {
    const post = vi.fn().mockResolvedValue({
      data: {
        code: 0,
        message: 'success',
        data: { id: 'MENU-001', status: 'PENDING_APPROVAL' },
      },
    });
    const api = new SmartCanteenApi({ post } as unknown as AxiosInstance);

    await api.submitMenu('MENU-001', {
      schoolId: 'SCHOOL-002',
      canteenId: 'CANTEEN-002',
    });

    expect(post).toHaveBeenCalledWith(
      '/api/v1/menus/MENU-001/submit',
      undefined,
      { params: { schoolId: 'SCHOOL-002', canteenId: 'CANTEEN-002' } },
    );
  });

  it('imports a recipe through the scoped recipe endpoint', async () => {
    const post = vi.fn().mockResolvedValue({
      data: {
        code: 0,
        message: 'success',
        data: {
          menuId: 'MENU-001',
          requirements: [{ materialId: 'FLOUR', quantity: 2, unit: 'kg' }],
        },
      },
    });
    const api = new SmartCanteenApi({ post } as unknown as AxiosInstance);

    const recipe = await api.importMenuRecipe(
      'MENU-001',
      [{ materialId: 'FLOUR', quantity: 2, unit: 'kg' }],
      { schoolId: 'SCHOOL-002', canteenId: 'CANTEEN-002' },
    );

    expect(post).toHaveBeenCalledWith(
      '/api/v1/menus/MENU-001/recipe',
      { requirements: [{ materialId: 'FLOUR', quantity: 2, unit: 'kg' }] },
      { params: { schoolId: 'SCHOOL-002', canteenId: 'CANTEEN-002' } },
    );
    expect(recipe.requirements[0].materialId).toBe('FLOUR');
  });

  it('maps the unified procurement plan endpoints with scope and idempotency headers', async () => {
    const get = vi.fn().mockResolvedValue({
      data: {
        code: 0,
        message: 'success',
        data: { total: 1, pages: 1, current: 1, size: 50, records: [] },
      },
    });
    const post = vi.fn().mockResolvedValue({
      data: {
        code: 0,
        message: 'success',
        data: {
          id: 'PLAN-001',
          planNo: 'PLAN-001',
          periodStart: '2026-08-14',
          periodEnd: '2026-08-14',
          status: 'DRAFT',
          version: 0,
          sourceMenuIds: [],
          items: [],
          orderIds: [],
        },
      },
    });
    const api = new SmartCanteenApi({ get, post } as unknown as AxiosInstance);
    const scope = { schoolId: 'SCHOOL-002', canteenId: 'CANTEEN-002' };

    await api.listProcurementPlans(scope);
    await api.generateProcurementPlanRange(
      '2026-08-14',
      '2026-08-14',
      'PLAN-KEY-1',
      scope,
    );

    expect(get).toHaveBeenCalledWith('/api/v1/procurement-plans', {
      params: { ...scope, page: 1, size: 50 },
    });
    expect(post).toHaveBeenCalledWith(
      '/api/v1/procurement-plans/generate-range',
      { periodStart: '2026-08-14', periodEnd: '2026-08-14' },
      { headers: { 'Idempotency-Key': 'PLAN-KEY-1' }, params: scope },
    );
  });

  it('maps purchase order transitions and remaining-quantity receipt', async () => {
    const get = vi.fn().mockResolvedValue({
      data: {
        code: 0,
        message: 'success',
        data: { total: 1, pages: 1, current: 1, size: 100, records: [] },
      },
    });
    const post = vi.fn()
      .mockResolvedValueOnce({
        data: {
          code: 0,
          message: 'success',
          data: {
            id: 'ORDER-001', orderNo: 'PO-001', supplierId: 'SUP-001',
            orderType: 'OFFLINE', status: 'CONFIRMED', totalAmount: 0, items: [],
          },
        },
      })
      .mockResolvedValueOnce({
        data: {
          code: 0,
          message: 'success',
          data: { orderId: 'ORDER-001', receiptId: 'RECEIPT-001', traceCodes: ['TRACE-001'] },
        },
      });
    const api = new SmartCanteenApi({ get, post } as unknown as AxiosInstance);
    const scope = { schoolId: 'SCHOOL-002', canteenId: 'CANTEEN-002' };

    await api.listPurchaseOrders(scope);
    await api.transitionPurchaseOrder('ORDER-001', 'CONFIRMED', scope);
    await api.receivePurchaseOrder('ORDER-001', 'RECEIPT-KEY-1', { items: [] }, scope);

    expect(get).toHaveBeenCalledWith('/api/v1/purchase-orders', {
      params: { ...scope, page: 1, size: 100 },
    });
    expect(post).toHaveBeenNthCalledWith(
      1,
      '/api/v1/purchase-orders/ORDER-001/status',
      { status: 'CONFIRMED' },
      { params: scope },
    );
    expect(post).toHaveBeenNthCalledWith(
      2,
      '/api/v1/purchase-orders/ORDER-001/receive',
      { items: [] },
      { headers: { 'Idempotency-Key': 'RECEIPT-KEY-1' }, params: scope },
    );
  });
});
