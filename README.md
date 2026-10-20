# 먹참 (meokcham)

> 먹고 싶었지만 참았다. 오늘도.

스스로 금지한 음식을 하루 단위로 "먹었는지/안 먹었는지" 기록하고,
그 결과를 GitHub 잔디(Contribution Graph) 형태로 한눈에 보여주는 서비스.

## 문서

- [PRD](docs/PRD.md)
- [기술 스택](docs/TECH_STACK.md)

## 구성

| 폴더 | 내용 |
| --- | --- |
| `backend/` | Spring Boot API 서버 (Java 21, Gradle) |
| `app/` | Expo(React Native) 앱 |
| `infra/` | Docker Compose 설정 |
| `docs/` | PRD, 기술 스택 문서 |

## 로컬 실행

필요한 도구: JDK 21, Node.js 22, Docker

```bash
# 1. PostgreSQL 실행
docker compose -p meokcham -f infra/docker-compose.local.yml up -d

# 2. 백엔드 실행 (http://localhost:8080, API 문서: /swagger-ui.html)
cd backend && ./gradlew bootRun

# 3. 앱 실행
cd app && npm install && npm start
```

- 백엔드 테스트: `cd backend && ./gradlew test` (Testcontainers가 PostgreSQL을 따로 띄운다)
- 앱 검사: `cd app && npm run typecheck && npm run lint`
- API 타입 생성: 백엔드를 실행한 상태에서 `cd app && npm run gen:api`
