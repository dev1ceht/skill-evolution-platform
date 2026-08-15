// Generated behavior tests for the typed API client.
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as client from './client';

const fetchMock = vi.fn();
vi.stubGlobal('fetch', fetchMock);
vi.stubGlobal('window', { location: { origin: 'https://contract.test' } });

describe("Resource API contract", () => {
  beforeEach(() => {
    fetchMock.mockReset();
    fetchMock.mockResolvedValue({
      ok: true,
      json: async () => ({
        code: 0,
        message: 'updated',
        data: { id: 'resource-1', name: 'Updated resource' },
      }),
    });
  });
  it("updateResource sends POST /api/v1/resources/{resource-id}", async () => {
    const result = await client.updateResource(
      'resource/with space',
      'idempotency-fixture',
      { name: 'Updated resource' },
    );
    const [requestUrl, options] = fetchMock.mock.calls[0];
    expect(new URL(requestUrl).pathname).toBe(
      "/api/v1/resources/resource%2Fwith%20space",
    );
    expect(options.method).toBe("POST");
    expect(options.headers["Idempotency-Key"]).toBe('idempotency-fixture');
    expect(options.headers["Content-Type"]).toBe("application/json");
    expect(JSON.parse(options.body)).toEqual({ name: 'Updated resource' });
    expect(result).toEqual({
      code: 0,
      message: 'updated',
      data: { id: 'resource-1', name: 'Updated resource' },
    });
  });
});
