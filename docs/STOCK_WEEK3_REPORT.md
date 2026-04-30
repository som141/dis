# 주식 시스템 3주차 작업 보고서

## 1. 목적

이번 3주차 작업의 목적은 `week-1`, `week-2`에서 준비한 stock persistence/quote 기반 위에
실제 거래 로직과 stock worker 내부 처리 흐름을 올려,
`gateway` 연동 직전 단계까지 stock-node를 실동작 가능한 상태로 만드는 것이었다.

문서 제목은 요청에 따라 `3주차`로 표기하지만,
실제 구현 범위는 계획서 기준 `3주차(2026.04.23 ~ 2026.04.29)`와
`4주차(2026.04.30 ~ 2026.05.06)`까지다.

반면 `5주차(2026.05.07 ~ 2026.05.13)`의 `gateway / Discord Slash Command 연동`은 아직 포함하지 않는다.

이번 작업의 기준은 아래와 같았다.

- 지급, 매수, 매도, 포지션/원장 갱신이 실제 DB에 반영될 것
- `balance`, `portfolio`, `history` 조회가 실제 데이터로 동작할 것
- RabbitMQ 기준 `command consume -> 처리 -> result publish` 흐름이 동작할 것
- 기능 추가와 테스트 추가를 같은 작업 단위로 묶을 것
- 테스트가 green이 된 단계만 완료로 인정할 것

## 2. 이번 주차에서 완료한 항목

### 2.1 계약 정렬

먼저 계획서와 실제 코드 계약의 어긋난 부분을 정리했다.

- `StockCommand.Buy`
  - 기존 초안은 `quantity` 중심이었지만, 계획서는 `amount` 기준 매수를 요구했다.
  - 이를 `amount` 기준 계약으로 수정했다.
- `StockCommand.Rank`
  - 기존 초안은 `limit` 중심이었지만, 이후 랭킹 주기 기준이 더 중요하므로 `period` 중심 계약으로 수정했다.

적용 파일:

- `modules/stock-core/src/main/java/discordgateway/stock/command/StockCommand.java`

이 단계로 이후 `TradeExecutionService`와 `gateway` slash-command 계약이 다시 어긋나지 않도록 기반을 고정했다.

### 2.2 DailyAllowanceService 구현

일일 지급 로직을 `DailyAllowanceService`로 구현했다.

핵심 동작은 다음과 같다.

- 계좌가 없으면 먼저 생성
- 명령 시점에 lazy settlement 방식으로 하루 10,000원 지급
- 같은 UTC 날짜 안에서는 중복 지급 방지
- 지급 시 `allowance_ledger` append
- 지급 후 `cash_balance` 갱신

적용 파일:

- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/DailyAllowanceService.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/AllowanceType.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/persistence/repository/AllowanceLedgerRepository.java`

이번 단계로 스케줄러 없이도 신규 사용자와 기존 사용자 모두 명령 진입 시점에 정상적인 지급 상태를 보장할 수 있게 되었다.

### 2.3 포지션 쓰기 모델과 평균단가 계산

기존 `StockPositionEntity`는 저장 구조만 있었고, 실제 매수/매도 갱신 규칙은 없었다.
이번 작업에서 다음을 추가했다.

- 매수 시 수량 증가
- 추가 매수 시 평균단가 재계산
- 매도 시 수량 감소
- 일부 매도 시 평균단가 유지
- 전량 매도 시 empty 상태 처리

같이 `StockAccountEntity`에도 현금 증감 메서드를 넣어 거래 서비스가 entity 규칙을 직접 활용할 수 있게 했다.

적용 파일:

- `apps/stock-node-app/src/main/java/discordgateway/stocknode/persistence/entity/StockPositionEntity.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/persistence/entity/StockAccountEntity.java`

이 단계로 거래 로직이 단순 CRUD 조합이 아니라 명확한 write model 위에서 동작하게 되었다.

### 2.4 Buy 실행 서비스 구현

`TradeExecutionService.buy(...)`를 구현했다.

핵심 규칙은 다음과 같다.

- 입력은 `amount` 기준
- `QuoteService`의 `TRADE` freshness 기준 사용
- 현재가 기준 체결 수량 계산
- 체결 금액만큼 현금 차감
- 포지션 생성 또는 수량 증가
- `trade_ledger`에 `BUY` append
- 전체 과정을 트랜잭션으로 묶음

추가한 예외:

- `InvalidTradeArgumentException`
- `InsufficientCashException`
- `StaleQuoteException`

적용 파일:

- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/TradeExecutionService.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/InvalidTradeArgumentException.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/InsufficientCashException.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/StaleQuoteException.java`

### 2.5 Sell 실행 서비스 구현

`TradeExecutionService.sell(...)`도 같은 서비스 안에 구현했다.

핵심 규칙은 다음과 같다.

- 입력은 `quantity` 기준
- 보유 수량 검증
- 현재가 기준 체결 금액 계산
- 현금 증가
- 포지션 수량 감소
- 전량 매도 시 포지션 삭제
- `trade_ledger`에 `SELL` append
- 전체 과정을 트랜잭션으로 묶음

추가한 예외:

- `InsufficientQuantityException`

적용 파일:

- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/TradeExecutionService.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/InsufficientQuantityException.java`

### 2.6 PortfolioService 구현

포트폴리오 계산은 `PortfolioService`로 분리했다.

계산 범위:

- 현금 잔고
- 보유 종목별 현재 평가금액
- 종목별 cost basis
- 종목별 손익
- 전체 평가금액
- 전체 자산
- 전체 손익

`QuoteService`를 재사용하되 조회 목적이므로 `QUERY` freshness 기준을 사용했다.

적용 파일:

- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/PortfolioService.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/PortfolioView.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/PortfolioPositionView.java`

### 2.7 조회 서비스 구현

4주차 범위의 조회 기능을 먼저 worker 내부에서 완성했다.

추가 서비스:

- `BalanceQueryService`
- `PortfolioQueryService`
- `TradeHistoryQueryService`

역할:

- `balance`
  - 계좌와 지급 상태를 보정한 뒤 현금 잔고 반환
- `portfolio`
  - 포트폴리오 계산 서비스 결과 반환
- `history`
  - 최근 거래내역 정렬 및 limit 반영

적용 파일:

- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/BalanceQueryService.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/BalanceView.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/PortfolioQueryService.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/TradeHistoryQueryService.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/TradeHistoryView.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/TradeHistoryEntryView.java`

### 2.8 결과 포맷 정리

worker 내부 결과를 gateway가 바로 사용할 수 있도록
`StockResponseFormatter`를 추가했다.

포맷한 응답:

- `quote`
- `buy`
- `sell`
- `balance`
- `portfolio`
- `history`
- 미구현 명령 메시지
- 예외 메시지

적용 파일:

- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/StockResponseFormatter.java`

이 단계로 비즈니스 로직과 사용자 응답 문자열을 분리했다.

### 2.9 StockCommandApplicationService 분기 구현

기존 `StockCommandApplicationService`는 스켈레톤이었다.
이번 작업에서 실제 분기 로직으로 바꿨다.

지원 명령:

- `quote`
- `buy`
- `sell`
- `balance`
- `portfolio`
- `history`

현재 처리 정책:

- 성공 명령은 `success=true`와 구체적인 `resultType` 반환
- 예외는 failure event로 변환
- `rank`는 아직 미구현이므로 `NOT_IMPLEMENTED` 응답 반환

적용 파일:

- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/StockCommandApplicationService.java`

### 2.10 RabbitMQ topology 및 publisher 구현

stock worker용 RabbitMQ 설정을 별도 클래스로 추가했다.

구현 내용:

- `DirectExchange` 기반 stock command exchange
- command queue 선언
- result exchange 선언
- routing key 규칙 사용
- `StockCommandResultPublisher` 구현

적용 파일:

- `apps/stock-node-app/src/main/java/discordgateway/stocknode/config/StockRabbitMessagingConfiguration.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/messaging/StockCommandResultPublisher.java`

이 구조는 기존 music 쪽 Rabbit 패턴과 책임 분리 원칙을 유지하는 방향으로 맞췄다.

### 2.11 StockCommandListener 구현

stock worker의 consume 진입점을 실제 구현했다.

구현 내용:

- `@RabbitListener` 기반 `StockCommandEnvelope` consume
- `StockCommandApplicationService` 호출
- 성공 시 result event 발행
- 예외 시 failure event 발행

적용 파일:

- `apps/stock-node-app/src/main/java/discordgateway/stocknode/messaging/StockCommandListener.java`

이 단계가 완료되면서 stock-node는 단독 비동기 worker로서 최소 명령 흐름을 끝까지 수행할 수 있게 되었다.

### 2.12 테스트용 설정 분리

통합 테스트를 안정적으로 돌리기 위해 설정을 분리했다.

- `stock.messaging.enabled`
- `stock.messaging.listener-enabled`
- `stock.quote.default-market`
- Testcontainers PostgreSQL driver override

적용 파일:

- `apps/stock-node-app/src/main/resources/application.yml`
- `apps/stock-node-app/src/test/resources/application-test.yml`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/bootstrap/StockQuoteProperties.java`
- `apps/stock-node-app/src/test/java/discordgateway/stocknode/integration/StockNodeIntegrationTestSupport.java`

## 3. 이번 주차에 추가하거나 수정한 주요 파일

### 수정한 파일

- `modules/stock-core/src/main/java/discordgateway/stock/command/StockCommand.java`
- `apps/stock-node-app/build.gradle`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/StockAccountApplicationService.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/StockCommandApplicationService.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/bootstrap/StockQuoteProperties.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/config/StockNodeComponentConfiguration.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/messaging/StockCommandListener.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/messaging/StockCommandResultPublisher.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/persistence/entity/StockAccountEntity.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/persistence/entity/StockPositionEntity.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/persistence/entity/TradeLedgerEntity.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/persistence/entity/AllowanceLedgerEntity.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/persistence/repository/AllowanceLedgerRepository.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/persistence/repository/TradeLedgerRepository.java`
- `apps/stock-node-app/src/main/resources/application.yml`
- `apps/stock-node-app/src/test/resources/application-test.yml`
- `apps/stock-node-app/src/test/java/discordgateway/stocknode/integration/StockNodeIntegrationTestSupport.java`
- `apps/stock-node-app/src/test/java/discordgateway/stocknode/integration/StockPersistenceIntegrationTest.java`
- `docs/README.md`

### 새로 추가한 주요 파일

- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/AllowanceType.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/TradeSide.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/DailyAllowanceService.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/TradeExecutionService.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/TradeExecutionResult.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/PortfolioService.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/BalanceQueryService.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/PortfolioQueryService.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/TradeHistoryQueryService.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/StockResponseFormatter.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/config/StockRabbitMessagingConfiguration.java`
- `apps/stock-node-app/src/test/java/discordgateway/stocknode/application/*`
- `apps/stock-node-app/src/test/java/discordgateway/stocknode/messaging/*`
- `apps/stock-node-app/src/test/java/discordgateway/stocknode/integration/StockTradingIntegrationTest.java`
- `apps/stock-node-app/src/test/java/discordgateway/stocknode/integration/StockMessagingIntegrationTest.java`

## 4. 검증 결과

### 4.1 세부 작업 단위 테스트

이번 주차에는 작업 단위마다 테스트를 같이 추가했다.

#### 계약 정렬 테스트

- `StockCommandContractTest`
  - `Buy`가 `amount` 계약으로 serialize/deserialize 되는지 검증
  - `Rank`가 `period` 계약으로 serialize/deserialize 되는지 검증

#### 포지션 write model 테스트

- `StockPositionWriteModelTest`
  - 추가 매수 시 평균단가 재계산
  - 일부 매도 시 평균단가 유지
  - 전량 매도 시 empty 상태 전환

#### 지급 서비스 테스트

- `DailyAllowanceServiceTest`
  - 첫 호출 시 10,000원 지급
  - 같은 날 중복 지급 방지
  - 다음 날 재지급

#### 거래 서비스 테스트

- `TradeExecutionServiceTest`
  - amount 기준 매수
  - 수량 기준 매도
  - stale quote 거절

#### 포트폴리오 계산 테스트

- `PortfolioServiceTest`
  - 복수 종목 평가금액 합산
  - cost basis / pnl 계산

#### 응답 포맷 테스트

- `StockResponseFormatterTest`
  - quote 메시지 포맷
  - portfolio 메시지 포맷
  - history 메시지 포맷

#### command dispatch 테스트

- `StockCommandApplicationServiceTest`
  - `quote` 분기
  - `buy` amount 분기
  - `rank` 미구현 분기

#### messaging 단위 테스트

- `StockCommandResultPublisherTest`
  - target node 기준 routing key 계산 검증
- `StockCommandListenerTest`
  - 성공 event 발행
  - 예외 시 failure event 발행

### 4.2 PostgreSQL / Redis 통합 테스트

기존 기반이 깨지지 않았는지 다시 검증했다.

- `StockPersistenceIntegrationTest`
  - Flyway schema 적용
  - unique constraint
  - account create/query
- `StockRedisIntegrationTest`
  - quote cache 저장/조회
  - lock acquire/release
  - provider rate-limit
  - 동시 quote refresh dedupe

### 4.3 거래 통합 테스트

새로 추가한 `StockTradingIntegrationTest`에서 아래를 검증했다.

- 지급 -> 매수 -> 포트폴리오 반영
- 매도 -> 포지션 소멸
- history 조회 시 BUY/SELL 순서 반영

### 4.4 RabbitMQ end-to-end 테스트

새로 추가한 `StockMessagingIntegrationTest`에서 아래를 검증했다.

- `quote` 명령 e2e
- `buy` 명령 e2e
- `history` 명령 e2e
- `command consume -> application dispatch -> result publish` 흐름 검증

### 4.5 실행한 검증 명령

실행 기준:

```powershell
.\gradlew.bat :apps:stock-node-app:test
.\gradlew.bat clean test
.\gradlew.bat bootJarAll
```

검증 경로:

- `C:\Users\s0302\.codex\memories\dis-ascii`

추가 확인:

- PostgreSQL / Redis / RabbitMQ Testcontainers 통합 테스트 통과
- 전체 `clean test` 통과
- 전체 `bootJarAll` 통과

### 4.6 주의사항

로컬 Windows 환경에서는 원본 작업 경로가 비ASCII 경로일 경우 Gradle test worker가 불안정할 수 있었다.

이 문제는 코드 문제가 아니라 경로/실행 환경 문제였고,
ASCII 경로에서 동일 테스트가 정상 통과하는 것을 확인했다.

또한 Testcontainers 기반 통합 테스트는 Docker daemon이 켜져 있어야 한다.

## 5. 이번 주차 완료 기준에 대한 판단

이번 주차 종료 시점 기준으로 아래가 충족되었다.

- stock command 계약 정렬 완료
- lazy daily allowance 로직 완료
- amount 기준 buy 로직 완료
- quantity 기준 sell 로직 완료
- 평균단가 계산 로직 완료
- portfolio 계산 로직 완료
- `balance`, `portfolio`, `history` 조회 서비스 완료
- `StockCommandApplicationService` 분기 완료
- stock RabbitMQ topology / publisher / listener 완료
- 세부 작업 단위 테스트 완료
- PostgreSQL 통합 테스트 완료
- Redis 통합 테스트 완료
- RabbitMQ end-to-end 테스트 완료
- 전체 `clean test` 완료
- 전체 `bootJarAll` 완료

따라서 실제 구현 범위 기준으로 `계획서 3주차 + 4주차`는 구현과 검증 기준 모두 완료된 상태로 본다.

## 6. 다음 주차 후속 작업

다음 단계에서 이어서 붙일 범위는 아래와 같다.

- `gateway-app`에 `/stock` slash command 등록
- stock command 발행
- stock result event 수신
- pending interaction reply edit 연결
- `rank` 계산 로직
- snapshot 생성 로직
- 실제 quote provider 연동

즉 다음 단계의 시작점은 `5주차 gateway 연동`이다.

## 7. 결론

이번 3주차 작업으로 저장소는 다음 상태가 되었다.

- 이전: stock persistence / quote 기반만 있고 실제 거래와 worker 메시징은 비어 있는 상태
- 현재: 지급, 매수, 매도, 조회, stock command dispatch, RabbitMQ worker 흐름까지 실제 테스트 완료된 상태

중요한 점은 기존 music bot 구조를 유지한 채 stock worker를 독립적으로 성장시켰다는 것이다.

즉, 현재 저장소는 음악 봇 아키텍처를 그대로 유지하면서도
주식 기능을 위한 실질적인 거래/조회/워커 처리 기반을 확보한 상태다.
