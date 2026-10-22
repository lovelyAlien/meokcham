# API 명세: 먹참

- 작성일: 2026-10-22
- 상태: 초안 (구현 전 합의용, D1~D4 확정)
- 관련 문서: [PRD](PRD.md), [기술 스택](TECH_STACK.md), [DB 스키마](DB_SCHEMA.md)

> **요약**
> - REST + JSON, 모든 경로는 `/api/v1` 아래. 로그인 외에는 `Authorization: Bearer <accessToken>`이 필요하다.
> - 핵심은 `PUT /records/{date}`(하루 한 건 upsert)와 `GET /grass`(1년치를 한 번에 조회).
> - 기록 요청은 **먹은 음식 ID 목록만** 받는다. 지킴/어김(`status`)과 금지 목록 스냅샷은 서버가 만든다.
> - 이 문서가 구현의 기준이다. 구현하면서 바뀌면 이 문서를 먼저 고친다. 실제 스펙(`/v3/api-docs`)은 코드에서 생성되므로, 앱 타입은 항상 생성본을 쓴다.

**목차**

1. [공통 규약](#1-공통-규약)
2. [인증](#2-인증)
3. [사용자](#3-사용자)
4. [금지 음식](#4-금지-음식)
5. [기록](#5-기록)
6. [잔디](#6-잔디)
7. [통계](#7-통계)
8. [알림](#8-알림)
9. [에러 코드](#9-에러-코드)
10. [엔드포인트 한눈에 보기](#10-엔드포인트-한눈에-보기)
11. [결정 기록 및 미결 사항](#11-결정-기록-및-미결-사항)

---

## 1. 공통 규약

| 항목 | 규칙 |
| --- | --- |
| 기본 경로 | `/api/v1` |
| 형식 | 요청·응답 모두 `application/json`, UTF-8 |
| 인증 | `Authorization: Bearer <accessToken>`. 인증이 필요 없는 곳은 `POST /auth/login`, `POST /auth/refresh`뿐 |
| 날짜 | `yyyy-MM-dd` (사용자 로컬 날짜, [PRD 5.3](PRD.md)의 04:00 경계 적용 후 값) |
| 시각(알림) | `HH:mm` (사용자 로컬 시각) |
| 일시 | ISO 8601 UTC (`2026-10-22T03:00:00Z`) |
| ID | 숫자(`bigint`). JSON 숫자로 내려준다 |
| 목록 응답 | 항상 객체로 감싼다(`{ "foods": [...] }`). 배열을 최상위로 내리지 않는다. 필드를 추가해도 호환이 깨지지 않게 하기 위함 |
| 페이지네이션 | 쓰지 않는다. 금지 음식은 사용자당 수십 개, 잔디는 최대 366건이다 |

### 1.1 "오늘"은 서버가 정한다

로컬 날짜 = `(현재 시각 - 4시간)`을 사용자 타임존으로 환산한 날짜다. 앱이 직접 계산하면 04:00 경계나 타임존 변경에서 어긋나므로, **"오늘" 날짜는 서버가 응답으로 알려준다**(`GET /grass`의 `today`, `GET /records/today`).

### 1.2 에러 응답

Spring의 ProblemDetail(RFC 9457)을 쓰고, 앱이 분기할 수 있도록 `code`를 추가한다.

```json
{
  "type": "about:blank",
  "title": "Conflict",
  "status": 409,
  "detail": "이미 같은 이름의 금지 음식이 있습니다.",
  "code": "FOOD_NAME_DUPLICATED"
}
```

- 앱은 `status`와 `code`로 분기하고, `detail`은 사용자에게 그대로 보여주지 않아도 된다(문구는 앱이 정한다).
- 입력 검증 실패(`400 VALIDATION_FAILED`)에는 `errors: [{ "field": "name", "message": "..." }]`를 더한다.
- 코드 목록은 [9. 에러 코드](#9-에러-코드).

### 1.3 멱등성

기록은 `PUT`이고 같은 날짜에 같은 내용을 여러 번 보내도 결과가 같다. 알림 액션과 앱에서 동시에 기록하거나, 네트워크 재시도로 두 번 도착해도 안전하다. 별도의 멱등성 키는 쓰지 않는다.

---

## 2. 인증

[기술 스택](TECH_STACK.md)대로 Apple/Google ID 토큰을 검증한 뒤 자체 Access JWT + Refresh 토큰을 발급한다.

| 토큰 | 수명(제안) | 보관 |
| --- | --- | --- |
| Access (JWT) | 15분 | 앱 메모리 |
| Refresh (불투명 문자열) | 30일 | 앱 `expo-secure-store`, 서버는 SHA-256 해시만 저장 |

### 2.1 `POST /auth/login`: 소셜 로그인

처음 로그인하면 가입까지 한 번에 처리한다(사용자·알림 설정 기본 행 생성).

**요청**

```json
{
  "provider": "APPLE",
  "idToken": "<소셜 SDK가 준 ID 토큰>",
  "timezone": "Asia/Seoul"
}
```

| 필드 | 설명 |
| --- | --- |
| `provider` | `APPLE` \| `GOOGLE` |
| `idToken` | 서버가 서명·`aud`·`exp`를 검증하고 `sub`로 사용자를 찾는다 |
| `timezone` | IANA 이름(`Asia/Seoul`). `+09:00` 같은 오프셋은 허용하지 않는다. 형식은 기존 사용자도 검증하지만 **신규 가입일 때만** 저장하고, 기존 사용자는 값을 무시한다(변경은 `PATCH /users/me`) |

**응답 `200`**

```json
{
  "accessToken": "eyJ...",
  "refreshToken": "r_...",
  "isNewUser": true,
  "user": { "id": 1, "timezone": "Asia/Seoul" }
}
```

**에러:** `400 VALIDATION_FAILED`(잘못된 타임존 포함), `401 INVALID_ID_TOKEN`

### 2.2 `POST /auth/refresh`: 토큰 재발급

Refresh 토큰을 쓸 때마다 **새 Refresh 토큰으로 교체(rotation)** 한다. 앱은 응답의 두 토큰을 모두 저장해야 한다.

**요청:** `{ "refreshToken": "r_..." }`

**응답 `200`:** `{ "accessToken": "eyJ...", "refreshToken": "r_..." }`

**에러:** `401 INVALID_REFRESH_TOKEN`(없음·만료·폐기). 이미 폐기된 토큰이 다시 오면 탈취로 보고 같은 family 전체를 폐기한 뒤 같은 에러를 돌려준다. 앱은 이 에러를 받으면 로그인 화면으로 보낸다.

### 2.3 `POST /auth/logout`: 로그아웃

현재 기기의 Refresh family를 폐기한다. 인증 필요.

**요청:** `{ "refreshToken": "r_..." }` → **응답 `204`**

### 2.4 Access 토큰 만료 처리 (앱 규칙)

`401`에 `code = ACCESS_TOKEN_EXPIRED`가 오면 `POST /auth/refresh`로 한 번 재발급한 뒤 원래 요청을 재시도한다. 재발급도 실패하면 로그아웃 처리한다. 그 외 `401`은 재시도하지 않는다.

---

## 3. 사용자

### 3.1 `GET /users/me`

**응답 `200`**

```json
{ "id": 1, "timezone": "Asia/Seoul", "createdAt": "2026-10-22T03:00:00Z" }
```

### 3.2 `PATCH /users/me`: 타임존 변경

**요청:** `{ "timezone": "America/Los_Angeles" }` → **응답 `200`**(`GET /users/me`와 같은 형태)

- 타임존을 바꿔도 **이미 저장된 기록의 `localDate`는 바뀌지 않는다.** 이후 기록부터 새 타임존으로 계산한다.
- 앱은 여행 등으로 기기 타임존이 바뀐 것을 감지하면 사용자에게 변경 여부를 묻는다(자동 변경은 하지 않는다).

### 3.3 `DELETE /users/me`: 계정 삭제

App Store 필수 요건. 사용자와 모든 자식 데이터를 하드 삭제한다([DB 스키마 2.1](DB_SCHEMA.md)). **응답 `204`**

---

## 4. 금지 음식

사용자당 활성(삭제되지 않은) 음식은 최대 **10개**다([결정 기록](#11-결정-기록-및-미결-사항) 참고). 소프트 삭제된 음식은 개수에 포함하지 않는다.

공통 응답 객체 `Food`:

```json
{ "id": 1, "name": "라면" }
```

### 4.1 `GET /foods`

삭제되지 않은 음식을 생성 순으로 내려준다.

**응답 `200`:** `{ "foods": [ { "id": 1, "name": "라면" }, { "id": 2, "name": "탄산" } ] }`

### 4.2 `POST /foods`: 추가

**요청:** `{ "name": "라면" }`

- 앞뒤 공백은 서버가 제거한다. 1~50자, 공백만으로는 안 된다.
- 온보딩에서 예시 목록을 여러 개 고르면 앱이 이 API를 여러 번 호출한다(일괄 API는 필요해질 때 추가).

**응답 `201`:** `Food`

**에러:** `400 VALIDATION_FAILED`, `409 FOOD_NAME_DUPLICATED`, `409 FOOD_LIMIT_EXCEEDED`

### 4.3 `PATCH /foods/{id}`: 이름 변경

**요청:** `{ "name": "인스턴트 라면" }` → **응답 `200`:** `Food`

- 이미 저장된 기록의 스냅샷에는 영향이 없다([PRD 5.3](PRD.md)).

**에러:** `400 VALIDATION_FAILED`, `404 FOOD_NOT_FOUND`, `409 FOOD_NAME_DUPLICATED`

### 4.4 `DELETE /foods/{id}`: 삭제

소프트 삭제. **응답 `204`**. 이미 삭제된 음식이나 남의 음식은 `404 FOOD_NOT_FOUND`.

---

## 5. 기록

기록 하나가 곧 하루 하나다. 요청에는 **먹은 음식 ID 목록**만 담는다.

- `eatenFoodIds`가 빈 배열이면 `KEPT`(지킴), 하나 이상이면 `BROKEN`(어김)으로 서버가 정한다. 클라이언트가 `status`를 보내지 않으므로 상태와 음식이 어긋날 수 없다.
- 서버는 저장 시점의 활성 금지 음식 목록을 `forbiddenSnapshot`으로 함께 저장한다.

공통 응답 객체 `Record`:

```json
{
  "date": "2026-10-22",
  "status": "BROKEN",
  "forbiddenSnapshot": [ { "id": 1, "name": "라면" }, { "id": 2, "name": "탄산" } ],
  "eatenFoods": [ { "id": 1, "name": "라면" } ],
  "updatedAt": "2026-10-22T14:20:00Z"
}
```

### 5.1 `PUT /records/{date}`: 기록 (생성 또는 덮어쓰기)

**요청**

```json
{ "eatenFoodIds": [1] }
```

지킨 날은 `{ "eatenFoodIds": [] }` 한 번이면 끝난다(10초 원칙).

**규칙**

| 규칙 | 에러 |
| --- | --- |
| `date`는 `today` 이전이어야 한다. **과거는 기간 제한 없이 소급·정정할 수 있다**([결정 기록](#11-결정-기록-및-미결-사항)). 미래 날짜는 불가 | `422 RECORD_DATE_OUT_OF_RANGE` |
| `eatenFoodIds`는 **현재 활성 금지 음식**에 속해야 하고 중복이 없어야 한다 | `422 UNKNOWN_FOOD` |
| 활성 금지 음식이 하나도 없으면 기록할 수 없다(지켰는지 판단할 대상이 없음) | `409 NO_FORBIDDEN_FOODS` |

- 같은 날짜에 다시 보내면 **그때의 금지 목록 기준으로 덮어쓴다.**
- 이미 있던 기록을 덮어써도, 새로 만들어도 같은 `200`과 `Record`를 돌려준다. 생성/갱신 구분이 앱에 필요하지 않다.

**응답 `200`:** `Record`

### 5.2 `PUT /records/today`: 오늘 기록 (별칭)

`PUT /records/{오늘 날짜}`와 같다. 알림에서 열린 앱이 04:00 경계를 직접 계산하지 않고도 오늘 칸에 기록해야 하므로 이 별칭을 쓴다.

### 5.3 `GET /records/{date}`: 하루 조회

**응답 `200`:** `Record` / 기록이 없으면 `404 RECORD_NOT_FOUND`(= 미기록). 소급 기록 화면(`record/[date]`)에서 기존 기록을 불러올 때 쓴다.

### 5.4 `GET /records/today`: 오늘 조회

홈 화면의 오늘 상태 카드용.

**응답 `200`**

```json
{
  "date": "2026-10-22",
  "record": null
}
```

`record`는 기록이 있으면 `Record`, 없으면 `null`이다. 이 엔드포인트는 미기록이어도 `200`이다(앱이 "오늘 날짜"를 알아야 하므로 404를 쓰지 않는다).

### 5.5 `DELETE /records/{date}`는 두지 않는다

기록을 지워 "미기록"으로 되돌리는 기능은 MVP에 없다. 잘못 기록했으면 `PUT`으로 정정한다.

---

## 6. 잔디

### 6.1 `GET /grass`: 최근 1년

1년치를 **단일 호출**로 내려준다([PRD 5.3](PRD.md) 조회 성능).

**쿼리**

| 이름 | 설명 | 기본값 |
| --- | --- | --- |
| `to` | 마지막 날짜 | 오늘 |
| `days` | 조회 일수 (1~366) | 366 |

**응답 `200`**

```json
{
  "today": "2026-10-22",
  "from": "2025-10-22",
  "to": "2026-10-22",
  "days": [
    { "date": "2026-10-20", "status": "KEPT", "eatenFoods": [] },
    { "date": "2026-10-21", "status": "BROKEN", "eatenFoods": [ { "id": 1, "name": "라면" } ] }
  ]
}
```

- **기록이 있는 날짜만** `days`에 담는다. 빈 날짜(미기록)는 앱이 `from`~`to` 범위로 채운다. 미기록은 행이 없는 것으로 표현하기 때문이다([DB 스키마 2.3](DB_SCHEMA.md)).
- `days`는 날짜 오름차순이다.
- 칸을 탭했을 때 날짜·상태·먹은 음식을 텍스트로 보여줘야 하므로([PRD 5.2](PRD.md) 접근성) `eatenFoods`를 함께 내려준다. 스냅샷은 크기 때문에 여기서는 제외하고, 필요하면 `GET /records/{date}`를 쓴다.
- `today`는 서버가 계산한 오늘 날짜이다([1.1](#11-오늘은-서버가-정한다)).

**에러:** `400 VALIDATION_FAILED`(`days` 범위 밖, 미래 `to`)

### 6.2 음식별 잔디 (F7, Could)

`GET /grass?foodId={id}`로 확장할 예정이다. 해당 음식이 `eatenFoods`에 포함된 날을 어김, 스냅샷에 포함됐지만 먹지 않은 날을 지킴, 스냅샷에 없던 날을 미기록처럼 구분해야 해서 응답 형태를 정하지 않았다. MVP에서는 구현하지 않는다.

---

## 7. 통계

### 7.1 `GET /stats`

조회 시 계산한다([DB 스키마 4](DB_SCHEMA.md): 캐시 컬럼 미사용).

**응답 `200`**

```json
{
  "today": "2026-10-22",
  "currentStreak": 4,
  "longestStreak": 12,
  "totalKeptDays": 37,
  "thisMonth": {
    "month": "2026-10",
    "keptDays": 15,
    "recordedDays": 20,
    "keptRate": 0.75
  }
}
```

**정의**

| 지표 | 정의 |
| --- | --- |
| `currentStreak` | 가장 최근 날짜부터 거꾸로 연속된 `KEPT` 일수. **오늘이 아직 미기록이면 어제부터 센다**(오늘 아직 기록 전이라는 이유로 스트릭이 0이 되면 안 된다). 하루라도 빈 날짜나 `BROKEN`이 있으면 거기서 끊긴다 |
| `longestStreak` | 전체 기간의 최장 연속 `KEPT` 일수 |
| `totalKeptDays` | 전체 `KEPT` 일수. 스트릭이 끊겨도 줄지 않는 지표([PRD 신념 4](PRD.md)) |
| `thisMonth.keptRate` | `keptDays / recordedDays` (기록한 날 중 지킨 날 비율). 기록이 없으면 `null` |

> 준수율의 분모는 "기록한 날"로 확정했다([결정 기록](#11-결정-기록-및-미결-사항)). 미기록이 벌점이 되지 않는 대신, 기록한 날 수가 적으면 비율이 왜곡될 수 있으므로 앱은 `recordedDays`를 함께 보여준다(예: "15/20일").

---

## 8. 알림

### 8.1 `GET /notification-settings`

**응답 `200`:** `{ "enabled": true, "notifyTime": "21:00" }`

가입 시 기본값 행이 생성되므로 항상 존재한다.

### 8.2 `PUT /notification-settings`

**요청:** `{ "enabled": true, "notifyTime": "21:30" }` → **응답 `200`**(조회와 같은 형태)

- `notifyTime`은 사용자 로컬 시각이다. 분 단위까지만 받는다.
- 시각을 바꾸면 그날 이미 발송됐다는 표시(`last_notified_on`)는 건드리지 않는다.

### 8.3 `POST /push-tokens`: 푸시 토큰 등록

앱은 로그인 직후와 앱 시작 시 호출한다. 같은 토큰이 이미 있으면 소유자를 현재 사용자로 옮기고 `last_seen_at`을 갱신한다(upsert, [DB 스키마 2.5](DB_SCHEMA.md)).

**요청:** `{ "token": "ExponentPushToken[xxxx]", "platform": "IOS" }` → **응답 `204`**

**에러:** `400 VALIDATION_FAILED`(`platform`은 `IOS` \| `ANDROID`)

### 8.4 `DELETE /push-tokens`: 토큰 해제

로그아웃 시 이 기기의 토큰을 지운다.

**요청:** `{ "token": "ExponentPushToken[xxxx]" }` → **응답 `204`**(없어도 `204`)

### 8.5 푸시 페이로드

서버가 Expo Push로 보내는 알림의 `data` 형식이다. 앱의 알림 카테고리(액션 버튼) 설정과 짝을 이룬다.

```json
{
  "title": "오늘 지켰나요?",
  "body": "먹고 싶었지만 참았나요?",
  "categoryId": "daily-check",
  "data": { "type": "DAILY_CHECK" }
}
```

| 액션 버튼 | 동작 |
| --- | --- |
| "안 먹었다" | 앱이 포그라운드로 열리면서 `PUT /records/today`에 `{ "eatenFoodIds": [] }`를 보내고, 완료 화면을 보여준다 |
| "먹었다" | 앱을 열어 오늘 기록 화면으로 보낸다(먹은 음식 선택이 필요하므로) |

두 버튼 모두 `opensAppToForeground: true`로 둔다. iOS에서 앱을 열지 않는 액션은 앱이 종료된 상태에서 JS까지 전달되지 않을 수 있어, 알림 → 1탭 기록을 보장하지 못한다([결정 기록](#11-결정-기록-및-미결-사항) D4). 3단계에서 실기기로 앱을 열지 않는 방식을 실험해 보고, 안정적이면 "안 먹었다"만 백그라운드 처리로 바꿀 수 있다. 이 경우에도 이 문서의 API는 바뀌지 않는다.

---

## 9. 에러 코드

| HTTP | `code` | 의미 |
| --- | --- | --- |
| 400 | `VALIDATION_FAILED` | 요청 본문·쿼리 형식 오류. `errors[]` 포함 |
| 401 | `INVALID_ID_TOKEN` | 소셜 ID 토큰 검증 실패 |
| 401 | `ACCESS_TOKEN_EXPIRED` | Access 토큰 만료. 앱이 재발급 후 재시도 |
| 401 | `UNAUTHORIZED` | 토큰 없음·위조 등. 재시도하지 않는다 |
| 401 | `INVALID_REFRESH_TOKEN` | Refresh 토큰 무효. 로그아웃 처리 |
| 404 | `FOOD_NOT_FOUND` | 없거나 삭제됐거나 남의 음식 |
| 404 | `RECORD_NOT_FOUND` | 해당 날짜 기록 없음(= 미기록) |
| 409 | `FOOD_NAME_DUPLICATED` | 같은 이름의 활성 금지 음식이 있음 |
| 409 | `FOOD_LIMIT_EXCEEDED` | 금지 음식 개수 한도 초과 |
| 409 | `NO_FORBIDDEN_FOODS` | 금지 음식이 없어 기록할 수 없음 |
| 422 | `RECORD_DATE_OUT_OF_RANGE` | 미래 날짜는 기록할 수 없음 |
| 422 | `UNKNOWN_FOOD` | `eatenFoodIds`에 활성 금지 음식이 아닌 ID가 있음 |
| 500 | `INTERNAL_ERROR` | 서버 오류. 내부 정보는 응답에 담지 않는다 |

남의 리소스는 존재 여부를 알리지 않도록 `403`이 아니라 `404`로 응답한다.

---

## 10. 엔드포인트 한눈에 보기

| 메서드 | 경로 | 설명 | 기능 |
| --- | --- | --- | --- |
| POST | `/auth/login` | 소셜 로그인(가입 포함) | 로그인 |
| POST | `/auth/refresh` | 토큰 재발급(rotation) | 로그인 |
| POST | `/auth/logout` | 로그아웃 | 로그인 |
| GET | `/users/me` | 내 정보 | 로그인 |
| PATCH | `/users/me` | 타임존 변경 | 로그인 |
| DELETE | `/users/me` | 계정 삭제 | 로그인 |
| GET | `/foods` | 금지 음식 목록 | F1 |
| POST | `/foods` | 금지 음식 추가 | F1 |
| PATCH | `/foods/{id}` | 이름 변경 | F1 |
| DELETE | `/foods/{id}` | 삭제(소프트) | F1 |
| GET | `/records/today` | 오늘 날짜와 기록 | F2 |
| PUT | `/records/today` | 오늘 기록 | F2, F4 |
| PUT | `/records/{date}` | 해당 날짜 기록·정정 | F2, F6 |
| GET | `/records/{date}` | 하루 상세 | F3, F6 |
| GET | `/grass` | 1년 잔디 | F3 |
| GET | `/stats` | 스트릭·준수율 | F5 |
| GET | `/notification-settings` | 알림 설정 조회 | F4 |
| PUT | `/notification-settings` | 알림 설정 변경 | F4 |
| POST | `/push-tokens` | 푸시 토큰 등록 | F4 |
| DELETE | `/push-tokens` | 푸시 토큰 해제 | F4 |

---

## 11. 결정 기록 및 미결 사항

### 11.1 확정 (2026-10-22)

| # | 질문 | 결정 | 영향 |
| --- | --- | --- | --- |
| D1 | 금지 음식 개수 한도 | **10개** (활성 음식 기준, 소프트 삭제 제외) | `POST /foods`가 한도를 넘으면 `409 FOOD_LIMIT_EXCEEDED`. 기록 시 먹은 음식 선택 목록과 스냅샷 크기가 작게 유지된다 |
| D2 | 월 준수율 분모 | **기록한 날** (`keptDays / recordedDays`) | 미기록이 벌점이 되지 않아 [신념 4](PRD.md)와 맞는다. 앱은 `recordedDays`를 함께 표시해 왜곡을 줄인다 |
| D3 | 소급 기록·정정 허용 기간 | **제한 없음** (미래 날짜만 불가) | 서버 상수가 필요 없다. 아래 영향 참고 |
| D4 | 알림 액션 버튼 방식 | **앱을 열어 자동 기록** (두 버튼 모두 `opensAppToForeground: true`) | iOS에서 앱을 열지 않는 액션은 종료 상태에서 JS까지 전달되지 않을 수 있음. 3단계 실기기 실험 후 "안 먹었다"만 백그라운드로 바꿀 수 있다 |

**D3의 영향 (알고 가야 할 것)**

- 오래된 날짜를 소급하면 스냅샷은 **그 날의 금지 목록이 아니라 지금의 목록**으로 저장된다. 과거의 목록은 복원할 수 없기 때문이다. 기록 시점 기준이라는 [PRD 5.3](PRD.md) 원칙과 일치하지만, 소급 기록의 스냅샷은 "기록한 시점의 목록"이라는 뜻이다.
- 소급으로 스트릭이 바뀔 수 있다. 빈 날을 `KEPT`로 채우면 `currentStreak`·`longestStreak`이 한꺼번에 늘 수 있고, 이는 의도된 동작이다(정직한 기록 원칙은 사용자 몫이다).
- 잔디는 최대 366일만 보여주므로 1년보다 오래된 소급 기록은 잔디에는 나타나지 않고 `totalKeptDays`와 최장 스트릭에만 반영된다.

### 11.2 미결 (해당 단계에서 정한다)

| # | 질문 | 제안 | 정해야 하는 시점 |
| --- | --- | --- | --- |
| 1 | Access/Refresh 수명 | 15분 / 30일 | 1단계(인증) 구현 시 |
| 2 | 온보딩 일괄 추가 API | 두지 않고 `POST /foods`를 반복 호출 | 앱 온보딩 화면 구현 시 |
| 3 | 오래된 푸시 토큰 정리 | `last_seen_at`이 오래된 토큰 삭제 배치. Expo의 `DeviceNotRegistered` 응답 삭제만으로 충분한지 확인 | 3단계(알림) 구현 시 |
| 4 | 알림 액션의 백그라운드 처리 가능 여부 | 실기기 실험(종료·백그라운드·포그라운드 3상태)으로 확인, D4 결과 조정 | 3단계 시작 시 |
| 5 | 음식별 잔디(F7) 응답 형태 | MVP 이후 | F7 착수 시 |
| 6 | 로그인 레이트 리밋 | MVP에서는 두지 않음. 공개 서비스로 전환할 때 검토 | 공개 운영 전 |
| 7 | ID 토큰 nonce 검증 | MVP에서는 하지 않음(탈취된 ID 토큰을 만료 전에 재사용할 수 있다). 공개 서비스로 전환할 때 도입 | 공개 운영 전 |
