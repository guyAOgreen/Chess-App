import { afterEach, describe, expect, it, vi } from 'vitest';
import { getJson, queryString } from './api';

function jsonResponse(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
  } as unknown as Response;
}

function nonJsonResponse(status: number): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => {
      throw new SyntaxError('Unexpected token < in JSON at position 0');
    },
  } as unknown as Response;
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('queryString', () => {
  it('is empty when nothing is set', () => {
    expect(queryString({})).toBe('');
    expect(queryString({ a: undefined, b: null, c: '' })).toBe('');
  });

  it('keeps zero, which is a page number and not an absent value', () => {
    expect(queryString({ page: 0 })).toBe('?page=0');
  });

  it('encodes values', () => {
    expect(queryString({ event: 'Hastings Premier' })).toBe('?event=Hastings+Premier');
  });

  it('drops only the absent entries', () => {
    expect(queryString({ result: 'DRAW', event: undefined, page: 2 })).toBe('?result=DRAW&page=2');
  });
});

describe('getJson', () => {
  it('returns the body of a successful response', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ status: 'UP' })));

    const response = await getJson<{ status: string }>('/actuator/health');

    expect(response).toEqual({ kind: 'body', ok: true, status: 200, data: { status: 'UP' } });
  });

  it('returns the body of a failed response, because some endpoints answer in it', async () => {
    // Actuator answers 503 with a well-formed body when a component is DOWN.
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse({ status: 'DOWN' }, 503)));

    const response = await getJson<{ status: string }>('/actuator/health');

    expect(response).toEqual({ kind: 'body', ok: false, status: 503, data: { status: 'DOWN' } });
  });

  it('reports a response that is not JSON as an invalid body, not as a dead network', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(nonJsonResponse(502)));

    const response = await getJson('/api/games');

    expect(response).toEqual({
      kind: 'invalid-body',
      ok: false,
      status: 502,
      message: 'Unexpected token < in JSON at position 0',
    });
  });

  it('reports a rejected fetch as unreachable', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));

    const response = await getJson('/api/games');

    expect(response).toEqual({ kind: 'unreachable', message: 'Failed to fetch' });
  });

  it("passes the caller's abort signal to fetch", async () => {
    const fetchStub = vi.fn().mockResolvedValue(jsonResponse({}));
    vi.stubGlobal('fetch', fetchStub);
    const controller = new AbortController();

    await getJson('/api/games', { signal: controller.signal });

    expect(fetchStub.mock.calls[0][1].signal).toBe(controller.signal);
  });

  it('always sends an Accept: application/json header, which a caller cannot override', async () => {
    const fetchStub = vi.fn().mockResolvedValue(jsonResponse({}));
    vi.stubGlobal('fetch', fetchStub);

    await getJson('/api/games');

    expect(fetchStub.mock.calls[0][1]).toEqual({
      headers: { Accept: 'application/json' },
      signal: undefined,
    });
  });
});
