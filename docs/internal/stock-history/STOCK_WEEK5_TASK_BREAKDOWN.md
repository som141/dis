# 주식 시스템 5주차 작업 분해

## 1. 목표

5주차 목표는 `gateway-app`에 `/stock` 명령을 연결해서 Discord 입력이 stock worker의 RabbitMQ command flow로 실제 전달되도록 만드는 것이다.

이번 주차에서 완료 기준은 아래와 같다.

- `/stock` slash command가 Discord command catalog에 등록될 것
- gateway가 stock command envelope을 생성하고 RabbitMQ로 발행할 것
- stock worker가 발행한 result event를 gateway가 받아 기존 interaction reply를 수정할 것
- 기존 music command 흐름은 유지될 것
- gateway 쪽 신규 경로에 대한 테스트가 추가될 것

## 2. 작업 단위

### W5-1. 공용 stock 메시징 계약 정리

- `stock-core`에 stock protocol 버전 상수 추가
- `StockMessagingProperties` 추가
- `StockCommandBus`, `StockCommandMessageFactory` 추가
- stock-node가 기존 로컬 messaging properties 대신 shared properties를 사용하도록 정리

테스트/검증:

- `clean test`에서 stock-node 기존 messaging 테스트가 계속 통과해야 함

완료 기준:

- gateway와 stock-node가 같은 stock exchange / routing key / result queue 규칙을 공유함

### W5-2. gateway stock command publish 계층 추가

- `StockApplicationService` 추가
- `RabbitStockCommandBus` 추가
- gateway bean wiring 추가

테스트/검증:

- `StockApplicationServiceTest`

완료 기준:

- gateway가 `quote`, `buy`, `sell`, `balance`, `portfolio`, `history`, `rank` command envelope을 만들 수 있음

### W5-3. Discord slash command catalog 확장

- `/stock` root command 추가
- subcommand 추가
  - `quote`
  - `buy`
  - `sell`
  - `balance`
  - `portfolio`
  - `history`
  - `rank`

테스트/검증:

- `DiscordCommandCatalogTest`

완료 기준:

- command catalog에 `/stock`과 필요한 subcommand가 모두 포함됨

### W5-4. Discord listener에서 stock 명령 파싱 및 dispatch

- `DiscordBotListener`에 stock 분기 추가
- symbol / amount / quantity / period / limit 파싱 추가
- defer reply + pending interaction 저장 재사용

테스트/검증:

- `DiscordBotListenerStockTest`

완료 기준:

- stock slash command가 gateway 내부에서 envelope로 변환되고 dispatch 호출까지 이어짐

### W5-5. gateway stock result listener 추가

- `RabbitStockCommandResultListener` 추가
- `InteractionResponseEditor` 추상화 추가
- 기존 music result listener도 같은 editor를 재사용하도록 정리

테스트/검증:

- `RabbitStockCommandResultListenerTest`

완료 기준:

- pending interaction이 있으면 stock result event로 기존 reply edit가 가능함

### W5-6. 전체 검증

실행 명령:

```powershell
.\\gradlew.bat clean test
.\\gradlew.bat bootJarAll
```

완료 기준:

- gateway-app 테스트 통과
- stock-node 기존 테스트 회귀 없음
- 전체 boot jar 패키징 성공

## 3. 메모

- Windows 원본 경로는 비ASCII 이슈가 있으므로 검증은 ASCII junction 경로에서 수행한다.
- 이번 주차는 gateway 연동까지만 포함한다.
- 실제 Discord에서 `/stock` command end-to-end 확인은 다음 배포/실행 단계에서 진행한다.
