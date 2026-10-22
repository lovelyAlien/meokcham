import * as AppleAuthentication from 'expo-apple-authentication';
import { Alert, Pressable, StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Spacing } from '@/constants/theme';
import { useAuth } from '@/features/auth/auth-store';
import type { SocialProvider } from '@/features/auth/social-login';
import { useColorScheme } from '@/hooks/use-color-scheme';
import { useTheme } from '@/hooks/use-theme';

export default function LoginScreen() {
  const signIn = useAuth((s) => s.signIn);
  const theme = useTheme();
  const scheme = useColorScheme();

  async function login(provider: SocialProvider) {
    try {
      await signIn(provider);
    } catch {
      Alert.alert('로그인하지 못했어요', '잠시 후 다시 시도해 주세요.');
    }
  }

  return (
    <ThemedView style={styles.container}>
      <SafeAreaView style={styles.content}>
        <ThemedText type="subtitle">먹참</ThemedText>
        <ThemedText themeColor="textSecondary">먹고 싶었지만 참았다. 오늘도.</ThemedText>

        <AppleAuthentication.AppleAuthenticationButton
          buttonType={AppleAuthentication.AppleAuthenticationButtonType.CONTINUE}
          buttonStyle={
            scheme === 'dark'
              ? AppleAuthentication.AppleAuthenticationButtonStyle.WHITE
              : AppleAuthentication.AppleAuthenticationButtonStyle.BLACK
          }
          cornerRadius={8}
          style={styles.button}
          onPress={() => login('APPLE')}
        />
        <Pressable
          accessibilityRole="button"
          style={[styles.button, styles.googleButton, { backgroundColor: theme.backgroundElement }]}
          onPress={() => login('GOOGLE')}>
          <ThemedText type="default">Google로 계속하기</ThemedText>
        </Pressable>
      </SafeAreaView>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  content: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: Spacing.two,
    paddingHorizontal: Spacing.four,
  },
  button: {
    width: '100%',
    maxWidth: 320,
    height: 44,
  },
  googleButton: {
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 8,
  },
});
