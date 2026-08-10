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
});
