# CI Testing Guide

## 1. CI에서 테스트는 어디서 도는가

GitHub Actions 워크플로우는 [`.github/workflows/cicd-deploy.yml`](../.github/workflows/cicd-deploy.yml) 이다.

현재 `main` 푸시 시 아래 Gradle 명령을 실행한다.

```bash
./gradlew clean test bootJarAll --no-daemon
```

즉, CI는 이제 `compile`만 확인하는 것이 아니라 `test`가 모두 통과해야 Docker image build와 remote deploy로 넘어간다.

## 2. CI 테스트 케이스는 어디서 확인하는가

테스트 코드 자체는 아래 경로에서 확인한다.

- `apps/stock-node-app/src/test/java`
- `apps/stock-node-app/src/test/resources`

대표 테스트 클래스:

- `StockNodeApplicationContextTest`
- `StockSchemaMigrationTest`
- `StockAccountRepositoryTest`
- `StockPositionRepositoryTest`
- `StockAccountApplicationServiceTest`
- `StockRedisKeyFactoryTest`
- `RedisQuoteRepositoryTest`
- `RedisLockServiceTest`
- `ProviderRateLimitServiceTest`
- `QuoteServiceTest`
- `StockPersistenceIntegrationTest`
- `StockRedisIntegrationTest`

## 3. GitHub Actions에서 결과를 보는 위치

GitHub UI 기준:

1. 저장소의 `Actions` 탭으로 이동
2. `Deploy Bot` 워크플로우 실행 선택
3. `build-and-deploy` job 확인
4. `Test and build with Gradle` step 로그 확인

추가로, CI는 이제 테스트 리포트를 artifact로 업로드한다.

artifact 이름:

- `gradle-test-reports-<git-sha>`

여기서 아래 내용을 내려받아 볼 수 있다.

- JUnit XML 결과
- HTML 테스트 리포트

## 4. 로컬에서 동일하게 확인하는 방법

기본 확인:

```powershell
.\gradlew.bat :apps:stock-node-app:test
.\gradlew.bat bootJarAll
```

CI와 동일한 흐름:

```powershell
.\gradlew.bat clean test bootJarAll
```

로컬 HTML 리포트 위치:

- `apps/stock-node-app/build/reports/tests/test/index.html`

JUnit XML 위치:

- `apps/stock-node-app/build/test-results/test`

## 5. 현재 CI에 포함되는 테스트 범위

### 5.1 단위 테스트

- Redis key 생성 규칙
- quote serialization / deserialization
- quote freshness 정책
- provider rate-limit 계산
- lock acquire / release

### 5.2 persistence 테스트

- Flyway migration 적용
- JPA entity / repository save/query
- unique constraint 검증
- account create/query 서비스

### 5.3 통합 테스트

- PostgreSQL 컨테이너 기반 schema / repository / account 검증
- Redis 컨테이너 기반 cache / lock / rate-limit / quote flow 검증

## 6. 로컬 실행 전 주의사항

- `StockPersistenceIntegrationTest`, `StockRedisIntegrationTest` 는 Docker가 켜져 있어야 한다.
- Windows에서 현재 원본 작업 경로가 비ASCII 경로일 경우 Gradle test worker가 흔들릴 수 있다.
- 그런 경우 ASCII 경로 또는 junction 경로에서 테스트를 실행하는 것이 안전하다.

## 7. 해석 기준

현재 CI에서 아래가 모두 통과하면 stock `week 1`, `week 2` 범위는 최소한 다음 수준까지 검증된 것으로 본다.

- 컴파일 성공
- 단위 테스트 성공
- PostgreSQL 통합 테스트 성공
- Redis 통합 테스트 성공
- 전체 `bootJarAll` 성공
