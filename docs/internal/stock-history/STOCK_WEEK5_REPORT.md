# 주식 시스템 5주차 작업 보고서

## 1. 목적

이번 5주차 작업의 목적은 이미 구현된 `stock-node-app`의 command 처리 흐름을 `gateway-app`과 연결해서 Discord slash command 입력이 실제 stock RabbitMQ flow로 들어가도록 만드는 것이다.

이번 주차의 기준은 아래와 같았다.

- 기존 music bot slash command 동작을 깨지 않는다.
- `gateway-app`만 Discord/JDA 진입점으로 유지한다.
- stock command/result messaging 규칙은 gateway와 stock-node가 공유한다.
- interaction pending 저장 구조는 기존 Redis 기반 구현을 재사용한다.
- 기능 추가와 테스트 추가를 같은 작업 단위로 묶는다.

## 2. 이번 주차에서 완료한 항목

### 2.1 공용 stock 메시징 계약 정리

`modules/stock-core`에 아래 항목을 추가했다.

- `StockProtocol`
- `StockMessagingProperties`
- `StockCommandBus`
- `StockCommandMessageFactory`

이로써 stock command exchange / routing key / result routing key / result queue 규칙을 gateway와 stock-node가 같은 타입으로 공유하게 되었다.

추가로 stock-node 쪽은 기존의 로컬 `StockNodeMessagingProperties`를 제거하고 shared `StockMessagingProperties`를 사용하도록 정리했다.

적용 파일:

- `modules/stock-core/build.gradle`
- `modules/stock-core/src/main/java/discordgateway/stock/messaging/*`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/config/StockNodeComponentConfiguration.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/config/StockRabbitMessagingConfiguration.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/messaging/StockCommandResultPublisher.java`

### 2.2 gateway stock command publish 계층 추가

`gateway-app`에 아래 계층을 추가했다.

- `StockApplicationService`
- `RabbitStockCommandBus`

현재 gateway는 아래 stock command를 생성해서 발행할 수 있다.

- `quote`
- `buy`
- `sell`
- `balance`
- `portfolio`
- `history`
- `rank`

적용 파일:

- `apps/gateway-app/src/main/java/discordgateway/gateway/application/StockApplicationService.java`
- `apps/gateway-app/src/main/java/discordgateway/gateway/messaging/RabbitStockCommandBus.java`
- `apps/gateway-app/src/main/java/discordgateway/gateway/config/GatewayComponentConfiguration.java`

### 2.3 Discord slash command catalog 확장

`DiscordCommandCatalog`에 `/stock` root command와 subcommand를 추가했다.

추가한 subcommand:

- `quote symbol`
- `buy symbol amount`
- `sell symbol quantity`
- `balance`
- `portfolio`
- `history [limit]`
- `rank period`

`buy`는 계획서 기준대로 금액 입력을 받도록 맞췄고, `sell`은 수량 입력을 받도록 유지했다.

적용 파일:

- `apps/gateway-app/src/main/java/discordgateway/gateway/presentation/discord/DiscordCommandCatalog.java`

### 2.4 Discord listener에서 stock 명령 파싱 및 dispatch 추가

`DiscordBotListener`에 stock 명령 분기를 추가했다.

이번 변경으로 listener는 아래를 수행한다.

- `stock` subcommand 식별
- symbol / amount / quantity / period / limit 파싱
- `StockApplicationService`로 envelope 생성
- `deferReply(true)` 후 pending interaction 저장
- Rabbit publish 실패 시 pending key 정리 후 기존 reply 수정

music command 흐름은 그대로 유지하고, deferred dispatch 경로만 공통화했다.

적용 파일:

- `apps/gateway-app/src/main/java/discordgateway/gateway/presentation/discord/DiscordBotListener.java`

### 2.5 gateway stock result listener 추가

`stock-node-app`이 발행하는 `StockCommandResultEvent`를 gateway가 받을 수 있도록 아래를 추가했다.

- `gatewayStockCommandResultQueue`
- `gatewayStockCommandResultDeclarables`
- `RabbitStockCommandResultListener`

또한 interaction reply edit 책임을 분리하기 위해 아래를 추가했다.

- `InteractionResponseEditor`
- `JdaInteractionResponseEditor`

기존 music result listener도 같은 editor를 사용하도록 정리했다. 이 변경으로 music/stock 모두 동일한 방식으로 pending interaction을 꺼내고 원래 reply를 수정한다.

적용 파일:

- `apps/gateway-app/src/main/java/discordgateway/gateway/interaction/InteractionResponseEditor.java`
- `apps/gateway-app/src/main/java/discordgateway/gateway/interaction/JdaInteractionResponseEditor.java`
- `apps/gateway-app/src/main/java/discordgateway/gateway/messaging/RabbitStockCommandResultListener.java`
- `apps/gateway-app/src/main/java/discordgateway/gateway/messaging/RabbitMusicCommandResultListener.java`
- `apps/gateway-app/src/main/java/discordgateway/gateway/config/GatewayComponentConfiguration.java`

### 2.6 gateway build / runtime wiring 확장

gateway build와 runtime 설정도 같이 정리했다.

- `gateway-app`가 `modules:stock-core`를 의존하도록 추가
- gateway test dependency 추가
- gateway `application.yml`에 stock messaging env 기반 설정 추가

적용 파일:

- `apps/gateway-app/build.gradle`
- `apps/gateway-app/src/main/resources/application.yml`

## 3. 이번 주차에 추가하거나 수정한 주요 파일

### 수정한 파일

- `modules/stock-core/build.gradle`
- `apps/gateway-app/build.gradle`
- `apps/gateway-app/src/main/java/discordgateway/gateway/config/GatewayComponentConfiguration.java`
- `apps/gateway-app/src/main/java/discordgateway/gateway/messaging/RabbitMusicCommandResultListener.java`
- `apps/gateway-app/src/main/java/discordgateway/gateway/presentation/discord/DiscordBotListener.java`
- `apps/gateway-app/src/main/java/discordgateway/gateway/presentation/discord/DiscordCommandCatalog.java`
- `apps/gateway-app/src/main/resources/application.yml`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/config/StockNodeComponentConfiguration.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/config/StockRabbitMessagingConfiguration.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/messaging/StockCommandResultPublisher.java`
- `apps/stock-node-app/src/test/java/discordgateway/stocknode/integration/StockMessagingIntegrationTest.java`
- `apps/stock-node-app/src/test/java/discordgateway/stocknode/messaging/StockCommandResultPublisherTest.java`

### 새로 추가한 주요 파일

- `modules/stock-core/src/main/java/discordgateway/stock/messaging/StockProtocol.java`
- `modules/stock-core/src/main/java/discordgateway/stock/messaging/StockMessagingProperties.java`
- `modules/stock-core/src/main/java/discordgateway/stock/messaging/StockCommandBus.java`
- `modules/stock-core/src/main/java/discordgateway/stock/messaging/StockCommandMessageFactory.java`
- `apps/gateway-app/src/main/java/discordgateway/gateway/application/StockApplicationService.java`
- `apps/gateway-app/src/main/java/discordgateway/gateway/interaction/InteractionResponseEditor.java`
- `apps/gateway-app/src/main/java/discordgateway/gateway/interaction/JdaInteractionResponseEditor.java`
- `apps/gateway-app/src/main/java/discordgateway/gateway/messaging/RabbitStockCommandBus.java`
- `apps/gateway-app/src/main/java/discordgateway/gateway/messaging/RabbitStockCommandResultListener.java`
- `apps/gateway-app/src/test/java/discordgateway/gateway/application/StockApplicationServiceTest.java`
- `apps/gateway-app/src/test/java/discordgateway/gateway/messaging/RabbitStockCommandResultListenerTest.java`
- `apps/gateway-app/src/test/java/discordgateway/gateway/presentation/discord/DiscordCommandCatalogTest.java`
- `apps/gateway-app/src/test/java/discordgateway/gateway/presentation/discord/DiscordBotListenerStockTest.java`

## 4. 검증 결과

### 4.1 gateway 신규 테스트

이번 주차에서 아래 gateway 테스트를 추가했다.

- `StockApplicationServiceTest`
- `RabbitStockCommandResultListenerTest`
- `DiscordCommandCatalogTest`
- `DiscordBotListenerStockTest`

각 테스트는 아래를 검증한다.

- stock envelope 생성 규칙
- stock result event 수신 시 pending interaction reply edit 호출
- `/stock` command catalog 구성
- listener의 stock quote / buy dispatch 흐름

### 4.2 기존 stock-node 회귀 검증

stock messaging properties를 shared 타입으로 치환했기 때문에 기존 stock-node 테스트도 계속 통과해야 했다.

이번 검증에서 아래가 그대로 통과했다.

- stock persistence 테스트
- Redis cache/lock 테스트
- stock messaging integration 테스트
- trading/query 테스트

즉, gateway 연동을 추가하면서 stock worker 기존 동작은 깨지지 않았다.

### 4.3 실행한 검증 명령

실행 기준:

```powershell
.\\gradlew.bat clean test
.\\gradlew.bat bootJarAll
```

### 4.4 주의사항

- 로컬 Windows 원본 경로는 비ASCII 경로 이슈가 있으므로 검증은 ASCII junction 경로에서 수행했다.
- 이번 주차는 gateway와 stock-node 사이의 command/result wiring까지 포함한다.
- 실제 Discord 서버에서 `/stock` command를 눌러 보는 실사용 검증은 배포 후 확인 단계가 남아 있다.

## 5. 이번 주차 완료 기준에 대한 판단

이번 주차 종료 시점 기준으로 아래가 충족되었다.

- `/stock` slash command catalog 추가 완료
- gateway stock command publish 계층 추가 완료
- gateway pending interaction 저장과 stock dispatch 연결 완료
- stock result event 수신 후 reply edit 경로 추가 완료
- stock messaging shared properties 정리 완료
- gateway 신규 테스트 완료
- 기존 stock-node 회귀 테스트 완료
- 전체 `bootJarAll` 완료

따라서 `week-5` 범위는 코드 구현과 테스트 기준 모두 완료된 상태로 본다.

## 6. 다음 주차 후속 작업

다음 주차에서 이어서 붙일 범위는 아래와 같다.

- snapshot 저장 구조와 생성 정책 정리
- `rank day/week/all` 실제 계산 로직 구현
- 길드 단위 랭킹 캐시 적용
- stale/fresh quote 기준을 랭킹 계산 경로에 맞춰 최종 정리
- Discord에서 `rank` 결과 포맷 실제 응답으로 연결

## 7. 결론

이번 5주차 작업으로 저장소는 다음 상태가 되었다.

- 이전: stock worker 내부 기능은 동작하지만 gateway `/stock` 명령은 아직 없음
- 현재: gateway가 `/stock` command를 등록하고 stock RabbitMQ command flow로 연결할 수 있는 상태

중요한 점은 기존 music bot의 Discord entry 구조를 유지하면서 stock command 흐름만 별도 경로로 안전하게 추가했다는 것이다.

즉, 현재 저장소는 음악 봇 아키텍처를 그대로 유지하면서도 stock 기능에 대한 Discord 진입점까지 확보한 상태다.
