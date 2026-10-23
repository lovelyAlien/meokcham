import { QueryClientProvider } from '@tanstack/react-query';
import { DarkTheme, DefaultTheme, Stack, ThemeProvider, useRouter } from 'expo-router';
import * as SplashScreen from 'expo-splash-screen';
import { useEffect } from 'react';
import { useColorScheme } from 'react-native';

import { useAuth } from '@/features/auth/auth-store';
import { queryClient } from '@/lib/query-client';

SplashScreen.preventAutoHideAsync();

export default function RootLayout() {
  const colorScheme = useColorScheme();
  const status = useAuth((s) => s.status);
  const bootstrap = useAuth((s) => s.bootstrap);
  const router = useRouter();

  useEffect(() => {
    bootstrap().finally(() => SplashScreen.hideAsync());
  }, [bootstrap]);

  // 보호된 라우트는 로그인 상태가 바뀌어도 자동으로 이동하지 않으므로(시작 경로 `/`가 로그아웃 상태에서는
  // 일치하는 화면이 없다) 세션 상태에 맞는 첫 화면으로 직접 보낸다.
  useEffect(() => {
    if (status !== 'loading') router.replace(status === 'signedIn' ? '/' : '/login');
  }, [status, router]);

  // 세션 복구가 끝날 때까지는 스플래시 화면을 유지한다.
  if (status === 'loading') return null;

  return (
    <QueryClientProvider client={queryClient}>
      <ThemeProvider value={colorScheme === 'dark' ? DarkTheme : DefaultTheme}>
        <Stack screenOptions={{ headerShown: false }}>
          <Stack.Protected guard={status === 'signedIn'}>
            <Stack.Screen name="(tabs)" />
            <Stack.Screen name="foods" />
            <Stack.Screen name="record/[date]" options={{ presentation: 'modal' }} />
          </Stack.Protected>
          <Stack.Protected guard={status === 'signedOut'}>
            <Stack.Screen name="(auth)" />
          </Stack.Protected>
        </Stack>
      </ThemeProvider>
    </QueryClientProvider>
  );
}
