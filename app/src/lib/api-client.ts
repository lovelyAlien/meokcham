/**
 * 백엔드 API 호출용 fetch 래퍼. 인증과 만료된 Access 토큰의 재발급을 맡는다.
 * 요청·응답 타입은 `npm run gen:api`로 백엔드 OpenAPI 스펙에서 생성한 `src/lib/api-types.ts`를 쓴다.
 */

import type { components } from './api-types';
import { secureTokenStore, type TokenStore } from './token-store';

type Tokens = Required<components['schemas']['TokenResponse']>;

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string | undefined,
    readonly path: string,
  ) {
    super(`API ${status}${code ? ` ${code}` : ''}: ${path}`);
  }
}

interface ClientDeps {
  baseUrl: string;
  fetchImpl: typeof fetch;
  store: TokenStore;
}

export function createApiClient({ baseUrl, fetchImpl, store }: ClientDeps) {
  let accessToken: string | null = null;
  let refreshing: Promise<boolean> | null = null;
  let onSignedOut: () => void = () => {};

  async function send(path: string, init?: RequestInit) {
    return fetchImpl(`${baseUrl}${path}`, {
      ...init,
      headers: {
        'Content-Type': 'application/json',
        ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}),
        ...init?.headers,
      },
    });
  }

  async function apiFetch<T>(path: string, init?: RequestInit, retried = false): Promise<T> {
    const response = await send(path, init);
    if (response.ok) {
      return (response.status === 204 ? undefined : await response.json()) as T;
    }
    const problem = await response.json().catch(() => ({}));
    // 만료된 Access 토큰만 한 번 재발급해서 재시도한다. 그 외의 401은 재시도하지 않는다.
    if (response.status === 401 && problem.code === 'ACCESS_TOKEN_EXPIRED' && !retried && (await refreshSession())) {
      return apiFetch<T>(path, init, true);
    }
    throw new ApiError(response.status, problem.code, path);
  }

  /** 동시에 여러 요청이 만료돼도 재발급은 한 번만 보낸다(Refresh 토큰은 한 번 쓰면 폐기되기 때문). */
  function refreshSession(): Promise<boolean> {
    refreshing ??= doRefresh().finally(() => {
      refreshing = null;
    });
    return refreshing;
  }

  // shortcut: 재발급 응답을 받기 전에 연결이 끊기면 서버는 토큰을 교체했는데 앱은 옛 토큰을 들고 있어
  // 다음 재발급이 재사용으로 탐지되어 로그아웃된다, 문제가 되면 서버에 짧은 유예 시간을 둔다.
  async function doRefresh(): Promise<boolean> {
    const refreshToken = await store.getRefresh();
    if (!refreshToken) return false;
    try {
      const response = await send('/api/v1/auth/refresh', {
        method: 'POST',
        body: JSON.stringify({ refreshToken }),
      });
      if (!response.ok) {
        // 서버가 토큰을 무효로 판단했을 때만 로그아웃한다. 서버 오류나 네트워크 문제로는 세션을 지우지 않는다.
        if (response.status === 401) await endSession();
        return false;
      }
      await setSession((await response.json()) as Tokens);
      return true;
    } catch {
      return false;
    }
  }

  async function setSession(tokens: Tokens) {
    accessToken = tokens.accessToken;
    await store.setRefresh(tokens.refreshToken);
  }

  async function clearSession() {
    accessToken = null;
    await store.clear();
  }

  async function endSession() {
    await clearSession();
    onSignedOut();
  }

  return {
    apiFetch,
    refreshSession,
    setSession,
    clearSession,
    getRefreshToken: () => store.getRefresh(),
    hasStoredSession: async () => (await store.getRefresh()) !== null,
    /** 재발급이 거부되어 세션이 끝났을 때 호출된다. 로그인 화면으로 보내는 일은 인증 스토어가 한다. */
    setOnSignedOut: (handler: () => void) => {
      onSignedOut = handler;
    },
  };
}

export const api = createApiClient({
  baseUrl: process.env.EXPO_PUBLIC_API_URL ?? 'http://localhost:8080',
  fetchImpl: (input, init) => fetch(input, init),
  store: secureTokenStore,
});

export const apiFetch = api.apiFetch;
