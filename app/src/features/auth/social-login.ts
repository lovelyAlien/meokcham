import * as AppleAuthentication from 'expo-apple-authentication';
import { GoogleSignin, isCancelledResponse } from '@react-native-google-signin/google-signin';

import { GOOGLE_IOS_CLIENT_ID } from '@/constants/auth';

export type SocialProvider = 'APPLE' | 'GOOGLE';

GoogleSignin.configure({ iosClientId: GOOGLE_IOS_CLIENT_ID });

/** 소셜 SDK로 로그인해 ID 토큰을 받는다. 사용자가 취소하면 null이다. */
export async function getIdToken(provider: SocialProvider): Promise<string | null> {
  return provider === 'APPLE' ? appleIdToken() : googleIdToken();
}

async function appleIdToken(): Promise<string | null> {
  try {
    const credential = await AppleAuthentication.signInAsync({
      requestedScopes: [AppleAuthentication.AppleAuthenticationScope.EMAIL],
    });
    if (!credential.identityToken) throw new Error('Apple ID 토큰이 없습니다.');
    return credential.identityToken;
  } catch (e) {
    if ((e as { code?: string }).code === 'ERR_REQUEST_CANCELED') return null;
    throw e;
  }
}

async function googleIdToken(): Promise<string | null> {
  const response = await GoogleSignin.signIn();
  if (isCancelledResponse(response)) return null;
  if (!response.data.idToken) throw new Error('Google ID 토큰이 없습니다.');
  return response.data.idToken;
}

/** 다음 로그인에서 계정 선택 화면이 다시 뜨도록 Google 쪽 세션도 지운다. */
export async function signOutOfProviders() {
  await GoogleSignin.signOut().catch(() => {});
}
