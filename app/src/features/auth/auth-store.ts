import { create } from 'zustand';

import { api, apiFetch } from '@/lib/api-client';
import type { components } from '@/lib/api-types';
import { queryClient } from '@/lib/query-client';
import { getIdToken, signOutOfProviders, type SocialProvider } from './social-login';

type LoginResponse = Required<components['schemas']['LoginResponse']>;

interface AuthState {
  /** loading: 저장된 세션을 확인하는 중 */
  status: 'loading' | 'signedOut' | 'signedIn';
  /** 앱 시작 시 한 번 호출한다. 저장된 Refresh 토큰으로 세션을 복구한다. */
  bootstrap: () => Promise<void>;
  /** 사용자가 취소하면 false, 로그인에 성공하면 true. 실패는 예외로 던진다. */
  signIn: (provider: SocialProvider) => Promise<boolean>;
  signOut: () => Promise<void>;
  deleteAccount: () => Promise<void>;
}

export const useAuth = create<AuthState>((set) => {
  async function endLocalSession() {
    await api.clearSession();
    await signOutOfProviders();
    queryClient.clear();
    set({ status: 'signedOut' });
  }

  // 재발급이 거부되면(만료·재사용 탐지) API 클라이언트가 알려 준다.
  api.setOnSignedOut(() => {
    queryClient.clear();
    set({ status: 'signedOut' });
  });

  return {
    status: 'loading',

    bootstrap: async () => {
      const restored = (await api.hasStoredSession()) && (await api.refreshSession());
      set({ status: restored ? 'signedIn' : 'signedOut' });
    },

    signIn: async (provider) => {
      const idToken = await getIdToken(provider);
      if (!idToken) return false;
      const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone;
      const login = await apiFetch<LoginResponse>('/api/v1/auth/login', {
        method: 'POST',
        body: JSON.stringify({ provider, idToken, timezone }),
      });
      await api.setSession(login);
      set({ status: 'signedIn' });
      return true;
    },

    signOut: async () => {
      // 서버 폐기가 실패해도 이 기기에서는 로그아웃한다.
      const refreshToken = await api.getRefreshToken();
      if (refreshToken) {
        await apiFetch('/api/v1/auth/logout', { method: 'POST', body: JSON.stringify({ refreshToken }) }).catch(() => {});
      }
      await endLocalSession();
    },

    deleteAccount: async () => {
      await apiFetch('/api/v1/users/me', { method: 'DELETE' });
      await endLocalSession();
    },
  };
});
