/**
 * 디자인 토큰. 색은 라이트/다크 모드별로 정의한다.
 * 잔디 3색(kept/broken/empty)은 두 모드 모두에서 서로 구분되어야 한다(PRD 5.2).
 */

import { Platform } from 'react-native';

export const Colors = {
  light: {
    text: '#1F2328',
    background: '#FFFFFF',
    backgroundElement: '#F0F0F3',
    backgroundSelected: '#E0E1E6',
    textSecondary: '#60646C',
    kept: '#2DA44E',
    broken: '#CF222E',
    empty: '#EBEDF0',
  },
  dark: {
    text: '#F0F6FC',
    background: '#0D1117',
    backgroundElement: '#212225',
    backgroundSelected: '#2E3135',
    textSecondary: '#B0B4BA',
    kept: '#3FB950',
    broken: '#F85149',
    empty: '#2D333B',
  },
} as const;

export type ThemeColor = keyof typeof Colors.light & keyof typeof Colors.dark;

export const Fonts = Platform.select({
  ios: {
    sans: 'system-ui',
    mono: 'ui-monospace',
  },
  default: {
    sans: 'normal',
    mono: 'monospace',
  },
});

export const Spacing = {
  half: 2,
  one: 4,
  two: 8,
  three: 16,
  four: 24,
  five: 32,
  six: 64,
} as const;
