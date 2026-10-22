# 실행 애플리케이션

DIS는 Gateway, 음악 worker, 주식 worker를 각각 별도 JVM과 컨테이너로 실행한다. 전체 실행은 [루트 README](../README.md)를 따르고, 특정 기능을 수정할 때 아래 앱에서 시작한다.

| 앱 / Compose 서비스 | 책임 | Java 진입점 | 호스트 HTTP 포트 |
| --- | --- | --- | --- |
| [gateway-app](gateway-app/README.md) / `gateway` | Discord 명령 수신, pending 저장, 결과 회신 | [GatewayApplication](gateway-app/src/main/java/discordgateway/gateway/GatewayApplication.java) | 8081 |
| [audio-node-app](audio-node-app/README.md) / `audio-node` | 음악 명령 소비, 재생·복구·음성 연결 | [AudioNodeApplication](audio-node-app/src/main/java/discordgateway/audionode/AudioNodeApplication.java) | 8082 |
| [stock-node-app](stock-node-app/README.md) / `stock-node` | 시세·모의매매·계좌·랭킹·시즌 | [StockNodeApplication](stock-node-app/src/main/java/discordgateway/stocknode/StockNodeApplication.java) | 8083 |

Gateway와 audio는 [common-core](../modules/common-core/README.md)를, Gateway와 stock은 [stock-core](../modules/stock-core/README.md)를 사용한다. 기본 통신 경로는 `gateway → RabbitMQ → worker → RabbitMQ → gateway`이며 `/man`, `/pizza`는 Gateway가 직접 응답한다.

명령 계약 변경은 발행 앱·계약 모듈·소비 앱을 함께 확인한다. [모듈 구조](../docs/reference/MODULE_STRUCTURE.md)와 [이벤트 계약](../docs/reference/EVENT_CONTRACT.md)에 상세 경계를 정리했다.
