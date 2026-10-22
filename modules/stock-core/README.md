# stock-core · 주식 메시지 계약

Gateway와 stock worker가 공유하는 Java 계약 라이브러리다. 두 앱이 같은 JSON 구조로 명령과 결과를 주고받도록 DTO·프로토콜·메시지 생성기를 제공한다. 독립 실행 앱은 아니다.

| 코드 | 역할 |
| --- | --- |
| [StockCommand](src/main/java/discordgateway/stock/command/StockCommand.java) | Quote·Buy·Sell·조회 등 명령 본문 |
| [StockCommandEnvelope](src/main/java/discordgateway/stock/command/StockCommandEnvelope.java) | commandId·schemaVersion·producer·응답 목적지 |
| [StockCommandResultEvent](src/main/java/discordgateway/stock/event/StockCommandResultEvent.java) | 성공 여부·사용자 메시지·결과 유형 |
| [StockCommandMessageFactory](src/main/java/discordgateway/stock/messaging/StockCommandMessageFactory.java) | 명령 메타데이터 생성 |
| [StockMessagingProperties](src/main/java/discordgateway/stock/messaging/StockMessagingProperties.java) | exchange·queue·routing key 기본값 |

이 모듈에는 거래 계산, JPA entity/repository, Redis/PostgreSQL 접근, Discord/JDA 또는 Finnhub 구현을 넣지 않는다. 이 구현은 각각 [stock-node-app](../../apps/stock-node-app/README.md)과 [gateway-app](../../apps/gateway-app/README.md)에 둔다.

계약 변경은 양쪽 앱과 [StockCommandContractTest](../../apps/stock-node-app/src/test/java/discordgateway/stocknode/application/StockCommandContractTest.java)를 함께 확인한다. 저장소 루트에서 `bash ./gradlew :modules:stock-core:build`로 빌드할 수 있다.

[모듈 목록](../README.md) · [이벤트 계약](../../docs/reference/EVENT_CONTRACT.md) · [모듈 구조](../../docs/reference/MODULE_STRUCTURE.md)
