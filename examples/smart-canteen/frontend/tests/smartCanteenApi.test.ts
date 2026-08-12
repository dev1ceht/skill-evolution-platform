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
});
