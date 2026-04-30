# 주식 시스템 3주차-4주차 작업 보고서

## 1. 목적

이번 3주차-4주차 작업의 목적은 `week-1`, `week-2`에서 준비한 persistence/quote 기반 위에
실제 거래 로직과 stock worker 내부 메시징 흐름을 붙여,
`gateway` 연동 직전 단계까지 stock-node를 실동작 가능한 상태로 올리는 것이다.

이번 주차 기준은 다음과 같았다.

- 지급, 매수, 매도, 포지션/원장 갱신이 실제 DB에 반영될 것
- `balance`, `portfolio`, `history` 조회가 실제 데이터로 동작할 것
- RabbitMQ 기준 `command consume -> 처리 -> result publish` 흐름이 동작할 것
- 각 단계마다 테스트를 추가하고 green 상태에서만 다음 단계로 넘어갈 것

## 2. 이번 주차에 완료한 항목

### 2.1 계약 정렬

`stock-core` 계약과 실제 계획서의 의미를 먼저 맞췄다.

- `StockCommand.Buy`
  - 기존 `quantity` 기준에서 `amount` 기준 매수 계약으로 변경
- `StockCommand.Rank`
  - 기존 `limit` 중심 초안에서 `period` 중심 계약으로 변경

적용 파일:

- `modules/stock-core/src/main/java/discordgateway/stock/command/StockCommand.java`

### 2.2 지급 및 거래 로직

다음 stock application 서비스를 추가했다.

- `DailyAllowanceService`
- `TradeExecutionService`
- `PortfolioService`
- `BalanceQueryService`
- `PortfolioQueryService`
- `TradeHistoryQueryService`

핵심 구현 내용:

- 하루 10,000원 lazy settlement 지급
- 금액 기준 매수
- 수량 기준 매도
- 평균단가 계산
- 거래 원장 append
- 거래 예외 처리
- 계좌/포지션/원장 갱신 트랜잭션 정리

적용 파일:

- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/DailyAllowanceService.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/TradeExecutionService.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/PortfolioService.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/BalanceQueryService.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/PortfolioQueryService.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/TradeHistoryQueryService.java`

### 2.3 쓰기 모델 보강

기존 entity를 조회 전용 수준에서 실제 갱신 가능한 상태로 확장했다.

- `StockAccountEntity`
  - 현금 증감 메서드 추가
- `StockPositionEntity`
  - 매수/매도 적용 메서드 추가
  - 평균단가 계산
  - 전량 매도 시 empty 상태 처리
- `TradeLedgerEntity`, `AllowanceLedgerEntity`
  - 조회/테스트용 getter 추가

적용 파일:

- `apps/stock-node-app/src/main/java/discordgateway/stocknode/persistence/entity/StockAccountEntity.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/persistence/entity/StockPositionEntity.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/persistence/entity/TradeLedgerEntity.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/persistence/entity/AllowanceLedgerEntity.java`

### 2.4 repository 확장

지급/내역 조회를 위해 repository 메서드를 보강했다.

- 일일 지급 중복 확인용 allowance ledger exists query
- trade history paging query

적용 파일:

- `apps/stock-node-app/src/main/java/discordgateway/stocknode/persistence/repository/AllowanceLedgerRepository.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/persistence/repository/TradeLedgerRepository.java`

### 2.5 결과 포맷과 command dispatch

worker 내부 처리 결과를 메시지로 만들기 위해 formatter와 dispatch를 추가했다.

- `StockResponseFormatter`
- `StockCommandApplicationService`

지원 명령:

- `quote`
- `buy`
- `sell`
- `balance`
- `portfolio`
- `history`

현재 `rank`는 의도적으로 `NOT_IMPLEMENTED` 응답을 반환한다.

적용 파일:

- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/StockResponseFormatter.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/StockCommandApplicationService.java`

### 2.6 RabbitMQ stock worker 흐름 구현

stock-node에 별도 RabbitMQ 설정을 추가하고, 실제 listener/publisher를 구현했다.

- `DirectExchange` 기반 stock topology 선언
- `StockCommandResultPublisher` 구현
- `@RabbitListener` 기반 `StockCommandListener` 구현
- 실패 시 failure event 발행

적용 파일:

- `apps/stock-node-app/src/main/java/discordgateway/stocknode/config/StockRabbitMessagingConfiguration.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/messaging/StockCommandResultPublisher.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/messaging/StockCommandListener.java`

### 2.7 설정 분리

테스트와 운영 시나리오를 분리하기 위해 messaging flag와 default market 설정을 추가했다.

- `stock.messaging.enabled`
- `stock.messaging.listener-enabled`
- `stock.quote.default-market`

적용 파일:

- `apps/stock-node-app/src/main/resources/application.yml`
- `apps/stock-node-app/src/test/resources/application-test.yml`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/bootstrap/StockQuoteProperties.java`

## 3. 이번 주차에 추가한 테스트

### 3.1 단위 테스트

- `StockCommandContractTest`
- `StockPositionWriteModelTest`
- `DailyAllowanceServiceTest`
- `TradeExecutionServiceTest`
- `PortfolioServiceTest`
- `StockResponseFormatterTest`
- `StockCommandApplicationServiceTest`
- `StockCommandResultPublisherTest`
- `StockCommandListenerTest`

### 3.2 통합 테스트

- `StockTradingIntegrationTest`
  - 지급 -> 매수 -> 매도 -> history 흐름 검증
- `StockMessagingIntegrationTest`
  - RabbitMQ `quote` e2e
  - RabbitMQ `buy` -> `history` e2e

### 3.3 기존 테스트 유지

다음 기존 week-1, week-2 테스트도 그대로 green 상태를 유지했다.

- `StockPersistenceIntegrationTest`
- `StockRedisIntegrationTest`
- `QuoteServiceTest`
- 기타 persistence/cache/lock 테스트

## 4. 검증 결과

로컬 검증은 Windows 비ASCII 경로 이슈를 피하기 위해 ASCII junction 경로에서 수행했다.

실행 경로:

- `C:\Users\s0302\.codex\memories\dis-ascii`

실행 명령:

```powershell
.\gradlew.bat :apps:stock-node-app:test
.\gradlew.bat clean test
.\gradlew.bat bootJarAll
```

검증 결과:

- `:apps:stock-node-app:test` 성공
- `clean test` 성공
- `bootJarAll` 성공

추가 확인:

- PostgreSQL / Redis / RabbitMQ Testcontainers 기반 통합 테스트 통과
- stock messaging e2e 통과

## 5. 이번 주차에 의도적으로 하지 않은 것

이번 3주차-4주차에서 의도적으로 미룬 항목은 다음과 같다.

- Discord `/stock` slash command 등록
- gateway pending interaction 연동
- stock result event를 gateway reply edit 흐름에 연결
- rank 계산 로직
- snapshot 생성 로직
- 실제 외부 quote provider 연동

이 항목들은 각각 5주차 이후 범위다.

## 6. 현재 상태 판단

이번 작업으로 저장소 상태는 다음과 같다.

- 이전
  - stock persistence / quote 기반만 있는 상태
- 현재
  - 지급, 매수, 매도, 조회, stock command dispatch, RabbitMQ worker 흐름까지 구현된 상태

즉 현재 stock-node는 `gateway`만 붙으면 `/stock` 명령의 실제 비동기 처리 워커로 바로 사용할 수 있는 수준까지 올라왔다.

## 7. 다음 주차 진입 조건

이제 5주차 작업으로 바로 넘어갈 수 있다.

다음 주차 핵심은 다음이다.

- gateway에 `/stock` slash command 등록
- stock command 발행
- stock result event 수신
- pending interaction reply edit 연결

이번 3주차-4주차 결과물은 그 전제 조건을 충족한다.
