import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as client from '../src/api/generated/client';

const fetchMock = vi.fn();
vi.stubGlobal('fetch', fetchMock);

beforeEach(() => {
  fetchMock.mockReset();
  fetchMock.mockResolvedValue({ ok: true, json: async () => ({ code: 0, message: 'success', data: [] }) });
});

describe('phase 6 Agent Runtime contract', () => {
  it('sends a versioned run confirmation', async () => {
    await client.decideAgentRun(
      'RUN-001', 'DECISION-KEY-001', 'SCHOOL-001', 'CANTEEN-001',
      { version: 0, decisionType: 'RUN_CONFIRM' }, 'REQ-001',
    );
    expect(String(fetchMock.mock.calls[0][0])).toContain('/api/v1/agent/runs/RUN-001/decisions');
    expect(fetchMock.mock.calls[0][1]).toEqual(expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({ version: 0, decisionType: 'RUN_CONFIRM' }),
    }));
  });

  it('exposes recovery events through the generated client', async () => {
    await client.getAgentRunEvents('RUN-001', 'SCHOOL-001', 'CANTEEN-001');
    expect(String(fetchMock.mock.calls[0][0])).toContain('/api/v1/agent/runs/RUN-001/events');
    expect(fetchMock.mock.calls[0][1]).toEqual(expect.objectContaining({ method: 'GET' }));
  });
});
