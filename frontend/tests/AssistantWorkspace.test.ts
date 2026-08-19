import { flushPromises, mount } from '@vue/test-utils';
import { describe, expect, it, vi } from 'vitest';
import AssistantWorkspace from '../src/components/AssistantWorkspace.vue';
import type { SmartCanteenApiPort } from '../src/api/smartCanteenApi';

const scope = { schoolId: 'SCHOOL-001', canteenId: 'CANTEEN-001' };

function apiWith(sendAssistantMessage: SmartCanteenApiPort['sendAssistantMessage']): SmartCanteenApiPort {
  return {
    sendAssistantMessage,
  };
}

describe('AssistantWorkspace', () => {
  it('sends a message and renders the structured assistant turn', async () => {
    const send = vi.fn().mockResolvedValue({
      conversationId: 'CONV-001',
      turnId: 'TURN-001',
      sequence: 1,
      kind: 'RESULT',
      message: '已完成溯源查询',
      intent: 'traceability.query',
      runId: 'RUN-001',
      runStatus: 'SUCCEEDED',
      result: { ingredientName: '青菜', batchId: 'BATCH-001', supplierName: '供应商 A' },
      missingFields: [],
      createdAt: '2026-08-17T05:00:00Z',
    });
    const wrapper = mount(AssistantWorkspace, { props: { api: apiWith(send), scope } });

    await wrapper.get('textarea').setValue('查询 TRACE-001 的食品溯源');
    await wrapper.get('form').trigger('submit');
    await flushPromises();

    expect(send).toHaveBeenCalledWith(
      expect.stringContaining('conversation-'),
      '查询 TRACE-001 的食品溯源',
      scope,
      expect.stringContaining('assistant-message-'),
    );
    expect(wrapper.get('[data-testid="assistant-message-assistant"]').text()).toContain('已完成溯源查询');
    expect(wrapper.text()).toContain('供应商 A');
  });

  it('shows an actionable error when the assistant request fails', async () => {
    const send = vi.fn().mockRejectedValue(new Error('助手服务不可用'));
    const wrapper = mount(AssistantWorkspace, { props: { api: apiWith(send), scope } });

    await wrapper.get('textarea').setValue('查询 TRACE-MISSING');
    await wrapper.get('form').trigger('submit');
    await flushPromises();

    expect(wrapper.get('[data-testid="assistant-error"]').text()).toContain('助手服务不可用');
  });

  it('hydrates the conversation from persisted history when available', async () => {
    const send = vi.fn();
    const history = vi.fn().mockResolvedValue({
      conversationId: 'CONV-HISTORY',
      status: 'ACTIVE',
      turns: [{
        userMessage: '查询 M001 的菜单',
        response: {
          conversationId: 'CONV-HISTORY',
          turnId: 'TURN-HISTORY',
          sequence: 1,
          kind: 'RESULT',
          message: '已查询菜单 M001',
          intent: 'menu.query',
          runId: 'RUN-HISTORY',
          runStatus: 'SUCCEEDED',
          result: { id: 'M001', status: 'PUBLISHED', items: [] },
          missingFields: [],
          createdAt: '2026-08-17T05:00:00Z',
        },
      }],
    });
    const api = { ...apiWith(send), getAssistantHistory: history };

    const wrapper = mount(AssistantWorkspace, { props: { api, scope } });
    await flushPromises();

    expect(history).toHaveBeenCalled();
    expect(wrapper.text()).toContain('查询 M001 的菜单');
    expect(wrapper.text()).toContain('已查询菜单 M001');
  });
});
