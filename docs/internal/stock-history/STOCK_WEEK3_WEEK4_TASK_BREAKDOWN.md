# Stock Week 3-4 Task Breakdown

## 1. 목적

이 문서는 현재 `week-0`, `week-1`, `week-2`까지 구현된 stock-node 기반 위에서
`week-3`, `week-4`를 바로 진행해도 되는지 판단하고,
실제 구현을 테스트 게이트 단위로 쪼개기 위한 작업 계획서다.

범위는 다음까지 포함한다.

- `week-3`: 지급, 매수, 매도, 포지션/원장 갱신, 거래 예외 처리
- `week-4`: 조회 기능, stock command 분기, RabbitMQ consume/publish 흐름

다음 범위는 아직 포함하지 않는다.

- Discord `/stock` slash command 등록과 gateway 연동 완료
- 길드 랭킹 계산
- snapshot 생성
- 실제 외부 quote provider 연동

## 2. 0~2주차 완료 여부 판단

현재 기준으로 `week-0`, `week-1`, `week-2`는 3~4주차로 넘어갈 수 있을 만큼 완료된 상태로 본다.

완료 근거는 다음과 같다.

- `week-0`
  - `modules/stock-core` 존재
  - `apps/stock-node-app` 존재
  - `docker-compose.yml`에 `postgres`, `stock-node` 추가 완료
  - `StockCommand`, `StockCommandEnvelope`, `StockCommandResultEvent` 초안 존재
- `week-1`
  - Flyway migration 존재
  - `stock_account`, `stock_position`, `trade_ledger`, `allowance_ledger`, `account_snapshot` 스키마 존재
  - JPA entity / repository 존재
  - `StockAccountApplicationService` 존재
- `week-2`
  - `RedisQuoteRepository`, `RedisLockService`, `ProviderRateLimitService`, `MockQuoteProvider`, `QuoteService` 존재
  - quote freshness / stale 정책 존재
- 검증
  - `StockPersistenceIntegrationTest` 존재
  - `StockRedisIntegrationTest` 존재
  - CI가 `clean test bootJarAll`을 수행하도록 반영됨

결론:

- `week-3`, `week-4`로 바로 진행 가능하다.
- 다만 `week-3` 첫 단계에서 계약과 쓰기 모델을 먼저 정렬해야 한다.

## 3. 선행 정리 사항

3~4주차에 들어가기 전에 먼저 고정해야 하는 항목이 있다.

### W3-0. 계약 및 모델 정렬

현재 계획서와 코드 사이에 다음 차이가 있다.

- `StockCommand.Buy`는 현재 `quantity`를 받지만 계획서는 `amount` 기준 매수를 요구한다.
- `StockPositionEntity`는 조회용 필드만 있고 매수/매도 갱신용 메서드가 없다.
- `trade_ledger`, `allowance_ledger`는 append 구조지만 side/type를 문자열로만 다루고 있다.

이번 단계에서 고정할 내용:

- `buy`는 금액 기준으로 처리한다.
- 수량은 내부 계산값으로만 만든다.
- `cashBalance`는 `scale=4`, `quantity`는 `scale=8` 기준으로 계산 규칙을 고정한다.
- 보유 수량이 `0`이 되면 포지션을 삭제할지 유지할지 정책을 고정한다.
- `TradeSide`, `AllowanceType`은 enum 또는 상수로 정리한다.

테스트:

- `StockCommandSerializationTest`
- `PortfolioWriteModelPolicyTest`

완료 기준:

- `buy` 의미가 더 이상 모호하지 않을 것
- 이후 매수/매도 서비스가 참조할 계산/삭제 정책이 문서와 코드로 고정될 것

## 4. Week 3 작업 단위

### W3-1. DailyAllowanceService

작업:

- `DailyAllowanceService` 추가
- `allowance_ledger` 기반 lazy settlement 구현
- 동일 일자 중복 지급 방지 로직 추가
- 신규 사용자 첫 명령 시 계좌 생성 후 지급까지 연결

산출물:

- 일일 10,000원 지급 서비스
- allowance ledger append 로직

테스트:

- `DailyAllowanceServiceTest`
  - 신규 사용자 첫 호출 시 10,000원 지급
  - 같은 날 두 번째 호출 시 추가 지급 없음
  - 다음 날 호출 시 한 번만 재지급
- `DailyAllowanceIntegrationTest`
  - PostgreSQL 기준 ledger append와 cash 반영 검증

완료 기준:

- lazy settlement가 스케줄러 없이 동작할 것
- 동일 날짜 중복 지급이 방지될 것

### W3-2. 포지션 쓰기 모델과 평균단가 계산

작업:

- `StockPositionEntity` 또는 별도 write service에 매수/매도 갱신 메서드 추가
- 평균단가 계산 정책 구현
- 최초 매수 시 포지션 생성
- 전량 매도 시 포지션 정리 정책 반영

산출물:

- 포지션 갱신 로직
- 평균단가 계산 로직

테스트:

- `StockPositionWriteModelTest`
  - 첫 매수 시 포지션 생성
  - 추가 매수 시 평균단가 재계산
  - 일부 매도 시 평균단가 유지
  - 전량 매도 시 정책대로 삭제 또는 0 수량 유지

완료 기준:

- 포지션 수량과 평균단가가 매수/매도 규칙에 맞게 갱신될 것

### W3-3. Buy 실행 서비스

작업:

- `StockBuyService` 또는 `TradeExecutionService.buy(...)` 구현
- `QuoteService`의 trade freshness 기준 사용
- 금액 기준 매수 처리
- 잔고 차감, 포지션 갱신, 거래 원장 append를 하나의 트랜잭션으로 묶기

산출물:

- 금액 기준 매수 서비스

테스트:

- `StockBuyServiceTest`
  - 매수 성공 시 cash 감소
  - 포지션 생성 또는 수량 증가
  - trade ledger append
  - 잔고 부족 시 예외
  - stale quote 시 거절
- `StockBuyIntegrationTest`
  - PostgreSQL + Redis + MockQuoteProvider 기준 매수 경로 검증

완료 기준:

- amount 기반 매수 후 계좌, 포지션, 거래 원장이 일관되게 갱신될 것

### W3-4. Sell 실행 서비스

작업:

- `StockSellService` 또는 `TradeExecutionService.sell(...)` 구현
- 수량 기준 매도 처리
- 보유 수량 검증
- 현금 증가, 포지션 감소, 거래 원장 append를 하나의 트랜잭션으로 묶기

산출물:

- 수량 기준 매도 서비스

테스트:

- `StockSellServiceTest`
  - 매도 성공 시 cash 증가
  - 포지션 수량 감소
  - trade ledger append
  - 보유 수량 부족 시 예외
  - stale quote 시 거절
- `StockSellIntegrationTest`
  - PostgreSQL + Redis + MockQuoteProvider 기준 매도 경로 검증

완료 기준:

- quantity 기반 매도 후 계좌, 포지션, 거래 원장이 일관되게 갱신될 것

### W3-5. PortfolioService

작업:

- `PortfolioService` 구현
- 계좌 현금 + 보유 포지션 평가금액 + 손익 계산 기반 마련
- week-4의 `balance`, `portfolio` 조회가 바로 사용할 수 있는 내부 모델 작성

산출물:

- 포트폴리오 계산 서비스

테스트:

- `PortfolioServiceTest`
  - 복수 종목 합산 평가
  - 평균단가 기반 손익 계산
  - 포지션이 없을 때 현금만 반환

완료 기준:

- week-4 조회 서비스가 재사용 가능한 내부 계산 결과를 반환할 것

### W3-6. 거래 예외와 트랜잭션 경계 정리

작업:

- `InsufficientCashException`
- `InsufficientQuantityException`
- `StaleQuoteException`
- `InvalidTradeArgumentException`
- 거래 서비스에 `@Transactional` 경계 정리
- 실패 시 partial write가 남지 않도록 롤백 보장

산출물:

- 명확한 거래 예외 체계
- 롤백 보장된 거래 서비스

테스트:

- `TradeTransactionRollbackTest`
  - ledger 저장 직전 예외 발생 시 account/position/ledger 롤백
- `TradeExceptionMappingTest`
  - 각 실패 조건이 올바른 예외로 매핑되는지 검증

완료 기준:

- 거래 실패 시 데이터 불일치가 남지 않을 것

### W3-7. Week 3 마감 검증

실행:

```powershell
.\gradlew.bat :apps:stock-node-app:test
.\gradlew.bat clean test bootJarAll
```

추가 검증:

- `StockTradingIntegrationTest`
  - 지급 -> 매수 -> 매도 -> 포지션/현금/원장 상태 전체 검증

완료 기준:

- 지급, 매수, 매도, 포지션 계산, 롤백 테스트가 모두 green일 것
- `bootJarAll`까지 통과할 것

## 5. Week 4 작업 단위

### W4-1. 조회 서비스

작업:

- `BalanceQueryService`
- `PortfolioQueryService`
- `TradeHistoryQueryService`
- 필요하면 `QuoteQueryService` 또는 quote formatter 추가

산출물:

- 잔고 조회
- 포트폴리오 조회
- 거래내역 조회

테스트:

- `BalanceQueryServiceTest`
- `PortfolioQueryServiceTest`
- `TradeHistoryQueryServiceTest`
  - 최근 거래 내역 정렬
  - limit 반영
  - 포지션/현금/평가금액 응답 검증

완료 기준:

- 조회형 명령이 실제 DB/quote 값을 바탕으로 결과를 만들 수 있을 것

### W4-2. 응답 뷰 모델과 메시지 포맷

작업:

- query/trade 결과용 view 또는 response model 추가
- gateway가 그대로 `editOriginal(...)` 할 수 있는 기본 메시지 포맷 정리
- quote, buy, sell, balance, portfolio, history 별 텍스트 포맷 통일

산출물:

- stock command 응답 포맷

테스트:

- `StockResponseFormatterTest`
  - 성공 메시지 포맷
  - 실패 메시지 포맷
  - history/balance/portfolio 텍스트 정렬 검증

완료 기준:

- 메시지 포맷이 서비스와 분리되어 재사용 가능할 것

### W4-3. StockCommandApplicationService 분기 구현

작업:

- `StockCommandApplicationService`에 command dispatch 구현
- 최소 지원 명령:
  - `quote`
  - `buy`
  - `sell`
  - `balance`
  - `portfolio`
  - `history`
- `rank`는 아직 미구현 상태를 명시적으로 반환

산출물:

- stock command -> application service 분기 처리

테스트:

- `StockCommandApplicationServiceTest`
  - 명령 타입별 올바른 서비스 호출
  - `commandId`, `targetNode`, `success`, `resultType` 반영
  - 미구현 명령 처리 검증

완료 기준:

- stock-core command를 실제 비즈니스 서비스에 연결할 수 있을 것

### W4-4. RabbitMQ topology와 publisher 구현

작업:

- stock command exchange/queue/result exchange/result routing key 선언
- music 쪽과 같은 `DirectExchange` 스타일로 맞추기
- `StockCommandResultPublisher` 구현
- publisher routing key는 `responseTargetNode` 또는 node-name 기준으로 계산

산출물:

- stock RabbitMQ topology
- result publisher

테스트:

- `StockRabbitTopologyContextTest`
  - exchange/queue/binding bean 생성 확인
- `StockCommandResultPublisherTest`
  - target node 기준 routing key 계산 검증

완료 기준:

- stock result event를 지정된 target node로 발행할 수 있을 것

### W4-5. StockCommandListener 구현

작업:

- `@RabbitListener` 기반 `StockCommandListener` 구현
- `StockCommandEnvelope` consume
- application service 호출
- 결과 event 발행
- 실패 시 실패 event 생성

산출물:

- stock command consume -> 처리 -> result publish 흐름

테스트:

- `StockCommandListenerTest`
  - envelope 수신 시 application service 호출
  - 성공 event 발행
  - 예외 발생 시 실패 event 발행

완료 기준:

- listener가 단일 명령 단위 처리 흐름을 끝까지 수행할 것

### W4-6. Rabbit end-to-end 통합 테스트

작업:

- RabbitMQ Testcontainers 도입
- 가능하면 PostgreSQL + Redis + RabbitMQ를 함께 띄운 stock e2e 테스트 추가
- command publish -> result consume까지 검증

산출물:

- stock messaging e2e 테스트

테스트:

- `StockMessagingIntegrationTest`
  - `quote` 명령 e2e
  - `buy` 명령 e2e
  - `history` 명령 e2e

완료 기준:

- 메시지 큐를 통한 비동기 흐름이 로컬 테스트에서 재현될 것

### W4-7. Week 4 마감 검증

실행:

```powershell
.\gradlew.bat :apps:stock-node-app:test
.\gradlew.bat clean test bootJarAll
```

추가 확인:

- GitHub Actions 기준 `clean test bootJarAll` green
- 필요하면 `docker compose up -d rabbitmq redis postgres stock-node` 후 health 확인

완료 기준:

- 조회 서비스, command dispatch, Rabbit consume/publish, e2e 테스트가 모두 green일 것
- 5주차 gateway 연동을 시작할 수 있을 것

## 6. 구현 순서

다음 순서로 진행한다.

1. `W3-0 계약 및 모델 정렬`
2. `W3-1 DailyAllowanceService`
3. `W3-2 포지션 쓰기 모델과 평균단가 계산`
4. `W3-3 Buy 실행 서비스`
5. `W3-4 Sell 실행 서비스`
6. `W3-5 PortfolioService`
7. `W3-6 거래 예외와 트랜잭션 경계 정리`
8. `W3-7 Week 3 마감 검증`
9. `W4-1 조회 서비스`
10. `W4-2 응답 뷰 모델과 메시지 포맷`
11. `W4-3 StockCommandApplicationService 분기 구현`
12. `W4-4 RabbitMQ topology와 publisher 구현`
13. `W4-5 StockCommandListener 구현`
14. `W4-6 Rabbit end-to-end 통합 테스트`
15. `W4-7 Week 4 마감 검증`

## 7. 바로 시작할 첫 작업 묶음

가장 먼저 시작할 묶음은 아래다.

1. `W3-0 계약 및 모델 정렬`
2. `W3-1 DailyAllowanceService`
3. `W3-2 포지션 쓰기 모델과 평균단가 계산`

이 세 단계가 끝나야 `buy/sell` 구현을 안전하게 붙일 수 있다.

특히 `StockCommand.Buy`의 의미를 먼저 고정하지 않으면
3주차 내부 서비스와 5주차 gateway slash-command 계약이 다시 어긋날 가능성이 높다.
