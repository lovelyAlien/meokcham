/**
 * 백엔드 API 호출용 fetch 래퍼.
 * 응답 타입은 `npm run gen:api`로 백엔드 OpenAPI 스펙에서 생성한 `src/lib/api-types.ts`를 쓴다.
 */

const baseUrl = process.env.EXPO_PUBLIC_API_URL ?? 'http://localhost:8080';

export async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${baseUrl}${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...init?.headers,
    },
  });
  if (!response.ok) {
    throw new Error(`API ${response.status}: ${path}`);
  }
  return (await response.json()) as T;
}
