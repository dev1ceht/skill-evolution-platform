import type { AxiosInstance } from 'axios';
import { describe, expect, it, vi } from 'vitest';
import { SmartCanteenApi } from '../src/api/smartCanteenApi';

describe('SmartCanteenApi', () => {
  it('sends an assistant message with explicit scope and idempotency', async () => {
    const post = vi.fn().mockResolvedValue({
      data: {
        code: 0,
        message: 'success',
        data: {
          conversationId: 'CONV-001',
          turnId: 'TURN-001',
          sequence: 1,
          kind: 'CLARIFICATION',
          message: '请提供批次溯源码',
          missingFields: ['traceCode'],
          createdAt: '2026-08-17T05:00:00Z',
        },
      },
    });
    const api = new SmartCanteenApi({ post } as unknown as AxiosInstance);
    const scope = { schoolId: 'SCHOOL-001', canteenId: 'CANTEEN-001' };

    const turn = await api.sendAssistantMessage(
      'CONV-001',
      '帮我查一下这批食材的溯源',
      scope,
      'assistant-message-001',
      'request-001',
    );

    expect(post).toHaveBeenCalledWith(
      '/api/v1/assistant/conversations/CONV-001/messages',
      { message: '帮我查一下这批食材的溯源' },
      {
        headers: {
          'Idempotency-Key': 'assistant-message-001',
          'X-Request-Id': 'request-001',
        },
        params: scope,
      },
    );
    expect(turn.kind).toBe('CLARIFICATION');
  });

  it('loads assistant conversation history with scope and limit', async () => {
    const get = vi.fn().mockResolvedValue({
      data: {
        code: 0,
        message: 'success',
        data: {
          conversationId: 'CONV-001',
          status: 'ACTIVE',
          turns: [],
        },
      },
    });
    const api = new SmartCanteenApi({ get } as unknown as AxiosInstance);

    const history = await api.getAssistantHistory(
      'CONV/001',
      { schoolId: 'SCHOOL-001', canteenId: 'CANTEEN-001' },
      20,
      'request-history-001',
    );

    expect(get).toHaveBeenCalledWith(
      '/api/v1/assistant/conversations/CONV%2F001/messages',
      {
        headers: { 'X-Request-Id': 'request-history-001' },
        params: { schoolId: 'SCHOOL-001', canteenId: 'CANTEEN-001', limit: 20 },
      },
    );
    expect(history.status).toBe('ACTIVE');
  });

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

  it('maps phase 3 governance queues and expiry scanning with the active scope', async () => {
    const get = vi.fn().mockResolvedValue({
      data: {
        code: 0,
        message: 'success',
        data: { total: 0, pages: 0, current: 1, size: 100, records: [] },
      },
    });
    const post = vi.fn().mockResolvedValue({
      data: { code: 0, message: 'success', data: [] },
    });
    const api = new SmartCanteenApi({ get, post } as unknown as AxiosInstance);
    const scope = { schoolId: 'SCHOOL-P3-API', canteenId: 'CANTEEN-P3-API' };

    await api.listComplianceRecords(scope);
    await api.listCanteenShowcases(scope);
    await api.listMealSuspensions(scope);
    await api.listSupplierComplaints(scope);
    await api.scanComplianceExpiry(scope, { windowDays: 30 });

    expect(get).toHaveBeenNthCalledWith(1, '/api/v1/compliance-records', {
      params: { ...scope, page: 1, size: 100 },
    });
    expect(get).toHaveBeenNthCalledWith(2, '/api/v1/canteen-showcases', {
      params: { ...scope, page: 1, size: 100 },
    });
    expect(post).toHaveBeenCalledWith(
      '/api/v1/compliance-records/expiry-scan',
      { windowDays: 30 },
      { params: scope },
    );
  });
});
