# 주식 시스템 1주차-2주차 작업 보고서

## 1. 목적

이번 1주차-2주차 작업의 목적은 `week-0`에서 만든 stock worker 스켈레톤 위에 실제로 동작 가능한 persistence 계층과 quote 조회 기반을 올리는 것이다.

이번 주차의 기준은 아래와 같았다.

- 기존 `gateway-app`, `audio-node-app` 음악 봇 동작을 깨지 않는다.
- stock 구현은 우선 `stock-node-app` 내부에서만 확장한다.
- `modules/stock-core`는 shared contract 전용으로 유지한다.
- PostgreSQL, Redis는 실제로 붙일 수 있는 구조까지 올린다.
- 기능 추가와 테스트 추가를 같은 작업 단위로 묶는다.

## 2. 이번 주차에서 완료한 항목

### 2.1 PostgreSQL persistence 배선

`apps/stock-node-app`에 아래 항목을 추가했다.

- Spring Data JPA
- Flyway
- datasource 설정
- PostgreSQL 지원 모듈

적용 파일:

- `apps/stock-node-app/build.gradle`
- `apps/stock-node-app/src/main/resources/application.yml`

이제 stock-node는 PostgreSQL을 기준으로 schema migration과 JPA repository를 함께 사용할 수 있다.

### 2.2 초기 stock schema 작성

Flyway migration `V1__init_stock_schema.sql`을 추가해 아래 테이블을 정의했다.

- `stock_account`
- `stock_position`
- `trade_ledger`
- `allowance_ledger`
- `account_snapshot`

주요 제약 조건:

- `(guild_id, user_id)` unique
- `(account_id, symbol)` unique

적용 파일:

- `apps/stock-node-app/src/main/resources/db/migration/V1__init_stock_schema.sql`

### 2.3 JPA entity 및 repository 추가

아래 entity를 추가했다.

- `StockAccountEntity`
- `StockPositionEntity`
- `TradeLedgerEntity`
- `AllowanceLedgerEntity`
- `AccountSnapshotEntity`
- `BaseTimeEntity`

아래 repository를 추가했다.

- `StockAccountRepository`
- `StockPositionRepository`
- `TradeLedgerRepository`
- `AllowanceLedgerRepository`
- `AccountSnapshotRepository`

이로써 account, position, ledger, snapshot에 대한 저장/조회 기반이 준비되었다.

### 2.4 account create/query 서비스 추가

`StockAccountApplicationService`를 추가했다.

현재 역할은 아래까지로 제한했다.

- `guildId + userId` 기준 account 조회
- 없으면 생성
- 이미 있으면 재사용

이번 주차에서는 의도적으로 아래는 넣지 않았다.

- 잔고 증감 로직
- 매수/매도 체결 로직
- 포트폴리오 계산 로직

### 2.5 Redis quote/cache/lock 기반 추가

2주차 범위로 아래 계층을 추가했다.

- `StockQuoteProperties`
- `StockRedisKeyFactory`
- `RedisQuoteRepository`
- `RedisLockService`
- `ProviderRateLimitService`
- `MockQuoteProvider`
- `QuoteService`

정리한 정책은 다음과 같다.

- quote cache key
- quote lock key
- provider minute/day budget key
- rank key
- query/trade/rank freshness 기준

즉, 실제 외부 시세 API는 아직 없지만 quote 조회 흐름 자체는 Redis와 mock provider 기준으로 검증 가능한 상태가 되었다.

### 2.6 stock-node 구성 배선 확장

`StockNodeComponentConfiguration`에서 아래 bean 배선을 추가했다.

- account application service
- quote repository
- quote lock service
- provider rate limiter
- quote provider
- quote service
- quote 관련 properties / clock

이제 stock-node는 단순 스켈레톤이 아니라 persistence와 quote 계층이 연결된 worker 형태가 되었다.

### 2.7 CI 테스트 게이트 강화

기존 GitHub Actions는 `compileTestJava`까지만 확인하고 배포를 진행했다.

이번 주차에서 다음으로 변경했다.

```bash
./gradlew clean test bootJarAll --no-daemon
```

즉, 이제 테스트가 통과해야만 Docker image build와 remote deploy가 진행된다.

추가로 테스트 리포트를 artifact로 업로드하도록 수정했다.

적용 파일:

- `.github/workflows/cicd-deploy.yml`

### 2.8 CI 테스트 문서화

CI 테스트 위치, 확인 방법, 로컬 재현 방법을 문서화했다.

추가 문서:

- `docs/operations/CI_TESTING.md`

## 3. 이번 주차에 추가하거나 수정한 주요 파일

### 수정한 파일

- `.github/workflows/cicd-deploy.yml`
- `apps/stock-node-app/build.gradle`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/config/StockNodeComponentConfiguration.java`
- `apps/stock-node-app/src/main/resources/application.yml`
- `docs/README.md`

### 새로 추가한 주요 파일

- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/StockAccountApplicationService.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/StockAccountSummary.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/bootstrap/StockQuoteProperties.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/cache/*`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/lock/*`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/persistence/entity/*`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/persistence/repository/*`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/quote/*`
- `apps/stock-node-app/src/main/resources/db/migration/V1__init_stock_schema.sql`
- `apps/stock-node-app/src/test/java/discordgateway/stocknode/*`
- `docs/operations/CI_TESTING.md`

## 4. 이번 주차에서 의도적으로 하지 않은 것

이번 1주차-2주차에서는 아래 항목을 구현하지 않았다.

- RabbitMQ stock command consume/publish 완성
- Discord `/stock` slash command 연결
- 실제 외부 quote provider 연동
- buy/sell execution
- balance/portfolio/history/rank 비즈니스 서비스 완성
- ranking 계산
- ledger/snapshot 실제 적재 로직 완성

이 항목들은 다음 주차 범위다.

## 5. 검증 결과

### 5.1 단위 및 애플리케이션 테스트

아래 테스트를 추가했다.

- application context 테스트
- migration 테스트
- repository 테스트
- account create/query 테스트
- Redis key 테스트
- Redis quote repository 테스트
- Redis lock 테스트
- provider rate-limit 테스트
- quote service 테스트

### 5.2 PostgreSQL 통합 테스트

Testcontainers 기반으로 아래를 검증했다.

- Flyway schema 적용
- repository save/query
- unique constraint
- account create/query

테스트 클래스:

- `StockPersistenceIntegrationTest`

### 5.3 Redis 통합 테스트

실제 Redis 컨테이너 기준으로 아래를 검증했다.

- quote cache 저장/조회
- lock acquire/release
- provider rate-limit
- 동시 quote refresh dedupe

테스트 클래스:

- `StockRedisIntegrationTest`

### 5.4 실행한 검증 명령

실행 기준:

```powershell
.\gradlew.bat :apps:stock-node-app:test
.\gradlew.bat bootJarAll
```

추가 검증:

- GitHub Actions 배포 워크플로우에서 `clean test bootJarAll`

### 5.5 주의사항

로컬 Windows 환경에서는 원본 작업 경로가 비ASCII 경로일 경우 Gradle test worker가 불안정할 수 있었다.

이 문제는 코드 문제가 아니라 경로/실행 환경 문제였고, ASCII 경로에서 동일 테스트가 정상 통과하는 것을 확인했다.

또한 Testcontainers 기반 통합 테스트는 Docker daemon이 켜져 있어야 한다.

## 6. 이번 주차 완료 기준에 대한 판단

이번 주차 종료 시점 기준으로 아래가 충족되었다.

- PostgreSQL schema 작성 완료
- JPA entity / repository 추가 완료
- account create/query 로직 추가 완료
- Redis quote cache / lock / rate-limit / quote service 추가 완료
- mock quote provider 추가 완료
- 단위 테스트 완료
- PostgreSQL 통합 테스트 완료
- Redis 통합 테스트 완료
- 전체 `bootJarAll` 완료
- CI 테스트 게이트 반영 완료

따라서 `week-1`, `week-2` 범위는 구현과 검증 기준 모두 완료된 상태로 본다.

## 7. 다음 주차 후속 작업

다음 주차에서 이어서 붙일 범위는 아래와 같다.

- stock command RabbitMQ flow 연결
- buy/sell execution
- ledger / snapshot 실제 쓰기
- balance / portfolio / history 서비스
- gateway `/stock` 명령 연결
- ranking 계산
- 실제 quote provider 연동

## 8. 결론

이번 1주차-2주차 작업으로 저장소는 다음 상태가 되었다.

- 이전: stock-node 스켈레톤만 있는 상태
- 현재: stock persistence와 quote 조회 기반이 실제 DB/Redis 검증까지 완료된 상태

중요한 점은 기존 music bot 구조를 유지한 채 stock worker를 독립적으로 성장시켰다는 것이다.

즉, 현재 저장소는 음악 봇 아키텍처를 그대로 유지하면서도 주식 기능을 위한 실질적인 1주차-2주차 기반을 확보한 상태다.
