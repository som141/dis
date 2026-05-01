# 주식 시스템 6주차-8주차 작업 보고서

## 1. 목적

이번 6주차-8주차 작업의 목적은 주식 시스템의 남은 핵심 기능을 마무리하는 것이다.

이전 주차 종료 시점에는 아래 상태였다.

- gateway `/stock` 연동 완료
- quote / buy / sell / balance / portfolio / history 완료
- `rank`는 gateway까지 연결됐지만 worker에서는 아직 미구현
- provider는 `MockQuoteProvider` 고정

이번 주차의 기준은 아래와 같았다.

- 기존 music bot 동작을 깨지 않는다.
- stock 기능은 계속 `stock-node-app` 중심으로 확장한다.
- rank 계산 규칙은 테스트로 먼저 고정한다.
- real provider는 선택 가능한 구조로 넣되, fallback 정책도 같이 테스트한다.
- 문서화와 테스트를 구현과 같은 수준으로 다룬다.

## 2. 이번 주차에서 완료한 항목

### 2.1 ranking period와 수익률 계산 규칙 추가

아래 항목을 추가했다.

- `RankingPeriod`
- `RankingEntryView`
- `RankingView`
- `RankingService`

현재 계산 기준은 아래와 같다.

- `DAY`
  - 해당 일자 시작 이후의 첫 snapshot을 baseline으로 사용
- `WEEK`
  - 해당 주 시작 이후의 첫 snapshot을 baseline으로 사용
- `ALL`
  - 누적 지급금(`allowance_ledger`) 대비 현재 총자산 기준 수익률 사용

적용 파일:

- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/RankingPeriod.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/RankingEntryView.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/RankingView.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/RankingService.java`

### 2.2 snapshot 서비스 및 스케줄러 추가

snapshot 테이블과 repository는 이미 있었지만 실제 쓰는 서비스가 없었다. 이번 주차에서 아래를 추가했다.

- `SnapshotService`
- `SnapshotScheduler`

현재 지원 범위:

- 단일 계좌 현재 snapshot 저장
- 전체 계좌 daily snapshot 저장
- 전체 계좌 weekly snapshot 저장
- 길드 기준 snapshot 저장

적용 파일:

- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/SnapshotService.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/SnapshotScheduler.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/persistence/repository/AccountSnapshotRepository.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/persistence/entity/AccountSnapshotEntity.java`

### 2.3 Redis 기반 rank cache 추가

ranking은 quote 조회와 계좌 평가를 포함하므로 cache를 붙였다.

추가한 항목:

- `RankingCacheRepository`
- `RedisRankingCacheRepository`

캐시 키는 기존 `StockRedisKeyFactory.rankKey(...)`를 사용한다.

추가로 rank cache가 거래 뒤에 stale 상태로 남지 않도록 아래 시점에 cache eviction을 넣었다.

- daily allowance 지급 직후
- buy 성공 직후
- sell 성공 직후

적용 파일:

- `apps/stock-node-app/src/main/java/discordgateway/stocknode/cache/RankingCacheRepository.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/cache/RedisRankingCacheRepository.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/cache/StockRedisKeyFactory.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/DailyAllowanceService.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/TradeExecutionService.java`

### 2.4 rank command 실제 구현

`StockCommandApplicationService`의 `Rank` 분기를 실제 동작으로 바꿨다.

이전:

- `NOT_IMPLEMENTED`

현재:

- `RankingService` 호출
- `StockResponseFormatter.formatRanking(...)` 호출
- `RANK` result event 반환

적용 파일:

- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/StockCommandApplicationService.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/StockResponseFormatter.java`

### 2.5 real provider 선택 구조 추가

provider를 `mock` 고정에서 설정 기반 선택 구조로 바꿨다.

추가한 항목:

- `StockProviderProperties`
- `AlphaVantageQuoteProvider`
- `FallbackQuoteProvider`

현재 지원 provider 타입:

- `mock`
- `alphavantage`

정책:

- `stock.provider.type=mock`
  - 기존 mock quote provider 사용
- `stock.provider.type=alphavantage`
  - Alpha Vantage 호출 시도
  - 실패하면 `fallback-to-mock=true`일 때 mock provider로 fallback

적용 파일:

- `apps/stock-node-app/src/main/java/discordgateway/stocknode/bootstrap/StockProviderProperties.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/quote/provider/AlphaVantageQuoteProvider.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/quote/provider/FallbackQuoteProvider.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/config/StockNodeComponentConfiguration.java`

### 2.6 실행 설정 반영

provider 구조를 실행 환경에 반영했다.

추가 env:

- `STOCK_PROVIDER_TYPE`
- `STOCK_PROVIDER_FALLBACK_TO_MOCK`
- `STOCK_PROVIDER_TIMEOUT`
- `ALPHAVANTAGE_BASE_URL`
- `ALPHAVANTAGE_API_KEY`
- `ALPHAVANTAGE_ENTITLEMENT`

적용 파일:

- `apps/stock-node-app/src/main/resources/application.yml`
- `.env.example`
- `docker-compose.yml`
- `apps/stock-node-app/src/test/resources/application-test.yml`

## 3. 이번 주차에 추가하거나 수정한 주요 파일

### 수정한 파일

- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/DailyAllowanceService.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/TradeExecutionService.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/StockCommandApplicationService.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/StockResponseFormatter.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/config/StockNodeComponentConfiguration.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/persistence/entity/AccountSnapshotEntity.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/persistence/repository/AccountSnapshotRepository.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/persistence/repository/AllowanceLedgerRepository.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/persistence/repository/StockAccountRepository.java`
- `apps/stock-node-app/src/main/resources/application.yml`
- `.env.example`
- `docker-compose.yml`
- `docs/README.md`
- `README.md`

### 새로 추가한 주요 파일

- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/RankingPeriod.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/RankingEntryView.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/RankingView.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/RankingService.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/SnapshotService.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/SnapshotScheduler.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/cache/RankingCacheRepository.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/cache/RedisRankingCacheRepository.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/bootstrap/StockProviderProperties.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/quote/provider/AlphaVantageQuoteProvider.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/quote/provider/FallbackQuoteProvider.java`
- `docs/internal/stock-history/STOCK_WEEK6_WEEK8_TASK_BREAKDOWN.md`
- `docs/internal/stock-history/STOCK_WEEK6_WEEK8_REPORT.md`

## 4. 검증 결과

### 4.1 application / unit 테스트

이번 주차에서 추가하거나 갱신한 주요 테스트:

- `RankingServiceTest`
- `DailyAllowanceServiceTest`
- `TradeExecutionServiceTest`
- `StockCommandApplicationServiceTest`
- `StockResponseFormatterTest`
- `AlphaVantageQuoteProviderTest`
- `FallbackQuoteProviderTest`

검증한 항목:

- 기간별 rank 계산 규칙
- rank cache hit 경로
- allowance / buy / sell 이후 rank cache invalidation
- rank formatter 출력
- rank command dispatch 결과 타입
- Alpha Vantage payload parse
- provider fallback 동작

### 4.2 통합 테스트

통합 테스트는 아래를 포함한다.

- `StockRankingIntegrationTest`
  - daily snapshot baseline 이후 rank 계산
- `StockMessagingIntegrationTest`
  - rank command RabbitMQ end-to-end 처리
- 기존 persistence / redis / trade / quote integration 테스트 회귀 확인

### 4.3 실행한 검증 명령

실행 기준:

```powershell
.\\gradlew.bat :apps:stock-node-app:test
.\\gradlew.bat clean test
.\\gradlew.bat bootJarAll
```

### 4.4 주의사항

- Windows 원본 경로 비ASCII 이슈 때문에 테스트는 ASCII junction 경로에서 수행했다.
- Alpha Vantage provider는 API key가 없으면 예외를 던지도록 했다.
- `alphavantage` 사용 시 `fallback-to-mock=true`면 primary 실패 시 mock quote로 내려간다.

## 5. 이번 주차 완료 기준에 대한 판단

이번 범위 종료 시점 기준으로 아래가 충족되었다.

- `rank day/week/all` 구현 완료
- snapshot service / scheduler 추가 완료
- Redis rank cache 적용 완료
- rank cache invalidation 추가 완료
- real provider 선택 구조 추가 완료
- provider fallback 테스트 완료
- env / compose / application 설정 반영 완료
- stock-node 세부 테스트 통과 완료
- 전체 `clean test` / `bootJarAll` 재검증 완료

따라서 남아 있던 `6주차-8주차` 범위는 구현과 검증 기준 모두 완료된 상태로 본다.

## 6. 결론

이번 6주차-8주차 작업으로 저장소는 다음 상태가 되었다.

- 이전: `/stock` 진입은 가능하지만 `rank`와 real provider는 미완성
- 현재: rank, snapshot, provider 선택, fallback, 실행 설정, 문서화까지 포함된 상태

즉, 현재 저장소는 기존 music bot 구조를 유지하면서 주식 시스템의 계획 범위를 실제 동작 가능한 수준까지 마무리한 상태다.
