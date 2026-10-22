import { ApiError, createApiClient } from './api-client';
import type { TokenStore } from './token-store';

const BASE = 'http://api.test';

function json(status: number, body?: unknown) {
  return Promise.resolve(new Response(body === undefined ? null : JSON.stringify(body), { status }));
}

function memoryStore(refresh: string | null): TokenStore & { value: () => string | null } {
  let current = refresh;
  return {
    getRefresh: async () => current,
    setRefresh: async (t) => {
      current = t;
    },
    clear: async () => {
      current = null;
    },
    value: () => current,
  };
}

const expired = () => json(401, { code: 'ACCESS_TOKEN_EXPIRED' });

describe('api client', () => {
  it('refreshes once on ACCESS_TOKEN_EXPIRED and retries the request with the new token', async () => {
    const store = memoryStore('r_old');
    const calls: { path: string; auth: string | null }[] = [];
    const fetchImpl = jest.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input).replace(BASE, '');
      calls.push({ path, auth: new Headers(init?.headers).get('Authorization') });
      if (path === '/api/v1/auth/refresh') return json(200, { accessToken: 'a_new', refreshToken: 'r_new' });
      return calls.filter((c) => c.path === '/api/v1/users/me').length === 1 ? expired() : json(200, { id: 1 });
    });
    const api = createApiClient({ baseUrl: BASE, fetchImpl: fetchImpl as typeof fetch, store });

    await expect(api.apiFetch('/api/v1/users/me')).resolves.toEqual({ id: 1 });

    expect(calls.map((c) => c.path)).toEqual(['/api/v1/users/me', '/api/v1/auth/refresh', '/api/v1/users/me']);
    expect(calls[2].auth).toBe('Bearer a_new');
    expect(store.value()).toBe('r_new');
  });

  it('sends a single refresh when several requests expire at the same time', async () => {
    const store = memoryStore('r_old');
    let refreshCalls = 0;
    const fetchImpl = jest.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      if (String(input).endsWith('/api/v1/auth/refresh')) {
        refreshCalls += 1;
        return json(200, { accessToken: 'a_new', refreshToken: 'r_new' });
      }
      return new Headers(init?.headers).get('Authorization') ? json(200, { ok: true }) : expired();
    });
    const api = createApiClient({ baseUrl: BASE, fetchImpl: fetchImpl as typeof fetch, store });

    await Promise.all([api.apiFetch('/a'), api.apiFetch('/b'), api.apiFetch('/c')]);

    expect(refreshCalls).toBe(1);
  });

  it('ends the session and reports sign-out when the refresh token is rejected', async () => {
    const store = memoryStore('r_old');
    const onSignedOut = jest.fn();
    const fetchImpl = jest.fn(async (input: RequestInfo | URL) =>
      String(input).endsWith('/api/v1/auth/refresh') ? json(401, { code: 'INVALID_REFRESH_TOKEN' }) : expired(),
    );
    const api = createApiClient({ baseUrl: BASE, fetchImpl: fetchImpl as typeof fetch, store });
    api.setOnSignedOut(onSignedOut);

    await expect(api.apiFetch('/api/v1/users/me')).rejects.toMatchObject({ status: 401, code: 'ACCESS_TOKEN_EXPIRED' });

    expect(store.value()).toBeNull();
    expect(onSignedOut).toHaveBeenCalledTimes(1);
  });

  it('keeps the session when refresh fails because of a server or network problem', async () => {
    const store = memoryStore('r_old');
    const onSignedOut = jest.fn();
    const fetchImpl = jest.fn(async (input: RequestInfo | URL) => {
      if (String(input).endsWith('/api/v1/auth/refresh')) throw new TypeError('Network request failed');
      return expired();
    });
    const api = createApiClient({ baseUrl: BASE, fetchImpl: fetchImpl as typeof fetch, store });
    api.setOnSignedOut(onSignedOut);

    await expect(api.apiFetch('/api/v1/users/me')).rejects.toBeInstanceOf(ApiError);

    expect(store.value()).toBe('r_old');
    expect(onSignedOut).not.toHaveBeenCalled();
  });

  it('does not refresh for other 401 errors', async () => {
    const store = memoryStore('r_old');
    const fetchImpl = jest.fn(async () => json(401, { code: 'UNAUTHORIZED' }));
    const api = createApiClient({ baseUrl: BASE, fetchImpl: fetchImpl as typeof fetch, store });

    await expect(api.apiFetch('/api/v1/users/me')).rejects.toMatchObject({ code: 'UNAUTHORIZED' });

    expect(fetchImpl).toHaveBeenCalledTimes(1);
  });

  it('returns undefined for 204 responses', async () => {
    const api = createApiClient({
      baseUrl: BASE,
      fetchImpl: (async () => json(204)) as typeof fetch,
      store: memoryStore(null),
    });

    await expect(api.apiFetch('/api/v1/users/me', { method: 'DELETE' })).resolves.toBeUndefined();
  });
});
