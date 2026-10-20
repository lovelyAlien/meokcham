# 기술 스택: 먹참

- 작성일: 2026-10-20
- 상태: 확정 (MVP)
- 관련 문서: [PRD](PRD.md)

> **요약**
> - 백엔드: Java + Spring Boot, PostgreSQL, JPA(CRUD) + 네이티브 SQL(집계), Flyway
> - 앱: Expo(React Native) + TypeScript, Expo Router, TanStack Query + Zustand
> - 인프라: 네이버 클라우드 서버 1대에 Docker Compose로 앱·DB를 함께 운영, CI는 GitHub Actions

버전은 프로젝트를 구성하는 시점의 최신 안정 버전으로 정하고, 정한 버전은 빌드 파일(`build.gradle.kts`, `package.json`)에 고정한다.

---

## 1. 전체 구성

```
[Expo 앱 (iOS/Android)]
   │  HTTPS (REST, JWT)
   ▼
[NCP 서버 ─ Docker Compose]
   ├── nginx           TLS 종료, 리버스 프록시
   ├── backend         Spring Boot (API + 알림 스케줄러)
   └── postgres        PostgreSQL (볼륨 + 정기 백업)
   │
   └──► Expo Push API ──► APNs / FCM ──► 기기 알림
```

## 2. 백엔드

| 항목 | 선택 | 이유 |
| --- | --- | --- |
| 언어·프레임워크 | Java + Spring Boot | 국내 백엔드 채용에서 가장 많이 쓰는 조합이라 포트폴리오·면접에서 설명하기 쉽다. 스케줄러·JPA·Security 생태계가 갖춰져 있다 |
| 빌드 | Gradle (Kotlin DSL) | Spring Initializr 기본값. 타입 안전한 빌드 스크립트 |
| DB | PostgreSQL | `generate_series`로 365칸 날짜 채우기, 윈도 함수로 스트릭 계산, `jsonb`로 금지 목록 스냅샷 저장이 자연스럽다 |
| 데이터 접근 | Spring Data JPA + 집계는 네이티브 SQL(JdbcClient) | CRUD는 JPA, 잔디·스트릭·준수율 같은 집계는 PostgreSQL 기능을 직접 쓴다. 도구마다 잘하는 일을 나눠 맡긴다 |
| 마이그레이션 | Flyway | 버전별 SQL 파일로 스키마 이력을 관리한다. `(user_id, local_date)` 유니크 제약 같은 DDL을 명시적으로 관리한다 |
| 인증 | Apple/Google ID 토큰 검증 → Access JWT + Refresh 토큰 | Access는 짧게, Refresh는 길게 둔다. Refresh 토큰은 PostgreSQL에 해시로 저장하고 재발급할 때마다 교체(rotation)한다. Redis를 쓰지 않는다 |
| API 문서 | springdoc-openapi | OpenAPI 스펙과 Swagger UI를 제공한다. 앱 타입 생성의 원천이 된다 |
| 알림 | `@Scheduled` + Expo Push API | 항상 켜진 서버에서 주기적으로 실행한다. 알림 시각이 됐고 아직 기록하지 않은 사용자에게만 보낸다 |
| 테스트 | JUnit 5 + Testcontainers(PostgreSQL) | 운영과 같은 DB로 네이티브 SQL과 제약 조건까지 검증한다 |

## 3. 앱

| 항목 | 선택 | 이유 |
| --- | --- | --- |
| 프레임워크 | Expo (React Native) + TypeScript | iOS 알림 액션 버튼을 쓰려면 네이티브 앱이어야 한다(PRD 결정 4) |
| 라우팅 | Expo Router | Expo 기본 템플릿, 파일 기반 라우팅 |
| 서버 상태 | TanStack Query | 캐싱과 낙관적 업데이트. 탭하면 바로 초록으로 바뀌어 10초 원칙을 지킨다 |
| 클라이언트 상태 | Zustand | 로그인 상태처럼 작은 전역 상태용 |
| 스타일링 | StyleSheet + 디자인 토큰 | 의존성이 없다. 색·간격 토큰과 다크 모드 테마만 직접 정의한다 |
| 잔디 렌더링 | react-native-svg | 7×53 격자를 그린다 |
| 토큰 보관 | expo-secure-store | Keychain/Keystore에 저장한다 |
| 알림 | expo-notifications | 푸시 토큰 등록, 액션 버튼(카테고리) 처리 |
| API 타입 | openapi-typescript | 백엔드 OpenAPI 스펙에서 TS 타입을 생성해 DTO 불일치를 컴파일 단계에서 잡는다 |

## 4. 인프라·운영

| 항목 | 선택 | 이유 |
| --- | --- | --- |
| 호스팅 | 네이버 클라우드(NCP) 서버 + Docker Compose | 국내 리전이라 지연이 적다. 항상 켜진 서버라 스케줄러가 멈추지 않는다 |
| DB 위치 | 같은 서버의 PostgreSQL 컨테이너 | DB 접근 지연이 없고 추가 비용이 없다. Neon은 서울 리전이 없어 쿼리마다 왕복 지연이 생겨서 제외했다 |
| 백업 | `pg_dump` 정기 실행 → NCP Object Storage | 직접 운영하는 DB라 백업을 직접 챙긴다(세부 사항은 운영 문서에서 정한다) |
| 로컬 개발 | Docker Compose(PostgreSQL) | 운영과 같은 구성 |
| CI | GitHub Actions | PR·push 때 바뀐 폴더별로 백엔드 테스트와 앱 타입체크·린트를 실행한다. 배포 자동화(CD)는 서버를 준비한 뒤 추가한다 |

## 5. 저장소 구조 (모노레포)

```
meokcham/
├── backend/            Spring Boot (Gradle)
├── app/                Expo (npm)
├── infra/              docker-compose, nginx 설정
├── docs/               PRD, 기술 스택, ADR
└── .github/workflows/  CI
```

## 6. 고려했지만 택하지 않은 것

| 대안 | 제외 이유 |
| --- | --- |
| PWA | iOS 웹 푸시는 홈 화면 추가가 필수이고 알림 액션 버튼을 지원하지 않는다 |
| Kotlin, NestJS | 국내 백엔드 채용 기준으로 Java + Spring이 포트폴리오 가치가 가장 크다 |
| BaaS(Supabase 등) | 백엔드 설계를 보여준다는 포트폴리오 목적과 맞지 않는다 |
| QueryDSL | 원본 프로젝트 관리가 멈췄고, 이 도메인에는 동적 검색 조건이 거의 없다 |
| Neon | 서울 리전이 없어 NCP 서버와의 왕복 지연이 생긴다 |
| Redis | Refresh 토큰 저장 외에는 쓸 곳이 없다. 이 규모에서는 PostgreSQL로 충분하다 |
| NativeWind | 빌드 설정이 늘어나고 Expo SDK 업그레이드 때 어긋날 위험이 있다 |
