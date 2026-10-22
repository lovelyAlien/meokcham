import { Alert, Pressable, StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Spacing } from '@/constants/theme';
import { useAuth } from '@/features/auth/auth-store';
import { useTheme } from '@/hooks/use-theme';

export default function SettingsScreen() {
  const { signOut, deleteAccount } = useAuth();
  const theme = useTheme();

  function confirmDelete() {
    Alert.alert('계정을 삭제할까요?', '모든 기록이 삭제되고 되돌릴 수 없어요.', [
      { text: '취소', style: 'cancel' },
      {
        text: '삭제',
        style: 'destructive',
        onPress: () => deleteAccount().catch(() => Alert.alert('삭제하지 못했어요', '잠시 후 다시 시도해 주세요.')),
      },
    ]);
  }

  return (
    <ThemedView style={styles.container}>
      <SafeAreaView style={styles.content}>
        <ThemedText type="subtitle">설정</ThemedText>
        <Pressable accessibilityRole="button" style={[styles.row, { backgroundColor: theme.backgroundElement }]} onPress={signOut}>
          <ThemedText>로그아웃</ThemedText>
        </Pressable>
        <Pressable accessibilityRole="button" style={[styles.row, { backgroundColor: theme.backgroundElement }]} onPress={confirmDelete}>
          <ThemedText style={{ color: theme.broken }}>계정 삭제</ThemedText>
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
  row: {
    width: '100%',
    maxWidth: 320,
    minHeight: 44,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 8,
  },
});
