import * as SecureStore from 'expo-secure-store';

const REFRESH_KEY = 'refreshToken';

/** Refresh 토큰만 기기 보안 저장소(Keychain)에 둔다. Access 토큰은 메모리에만 둔다. */
export interface TokenStore {
  getRefresh(): Promise<string | null>;
  setRefresh(token: string): Promise<void>;
  clear(): Promise<void>;
}

export const secureTokenStore: TokenStore = {
  getRefresh: () => SecureStore.getItemAsync(REFRESH_KEY),
  setRefresh: (token) => SecureStore.setItemAsync(REFRESH_KEY, token),
  clear: () => SecureStore.deleteItemAsync(REFRESH_KEY),
};
