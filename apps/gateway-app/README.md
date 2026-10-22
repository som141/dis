# gateway-app · Discord 명령과 응답

Gateway는 slash command를 명령 메시지로 바꿔 RabbitMQ에 보내고, worker 결과를 받아 Discord 응답을 편집한다. `/man`과 `/pizza`는 worker 없이 직접 응답한다.

## 코드 읽기 순서

1. [GatewayApplication](src/main/java/discordgateway/gateway/GatewayApplication.java): 앱 시작.
2. [DiscordCommandCatalog](src/main/java/discordgateway/gateway/presentation/discord/DiscordCommandCatalog.java): 명령·필수 옵션·`/man` 내용의 기준.
3. [DiscordBotListener](src/main/java/discordgateway/gateway/presentation/discord/DiscordBotListener.java): 명령 분기, envelope 준비, `deferReply` 뒤 pending 저장과 발행.
4. [MusicApplicationService](src/main/java/discordgateway/gateway/application/MusicApplicationService.java), [StockApplicationService](src/main/java/discordgateway/gateway/application/StockApplicationService.java): command 준비·발행.
5. [interaction/](src/main/java/discordgateway/gateway/interaction): Redis의 `commandId → interaction context`와 실제 응답 편집.
6. [messaging/](src/main/java/discordgateway/gateway/messaging): 음악·주식 결과 수신 후 pending 조회·회신.

음악의 재생 접수 후 `PLAYBACK_FAILED`가 오면 같은 원래 응답을 실패 안내로 바꾼다. 성공한 `/play`·`/sfx`의 pending은 후속 실패를 위해 남기고, terminal failure는 소비한다. interaction 유효 기간은 15분이다.

## 실행과 의존성

- Compose 서비스: `gateway`, 호스트 HTTP `8081` → 컨테이너 `8080`.
- 필수 연결: Discord, RabbitMQ, Redis. autocomplete·음원 조회를 위한 PlayerManager와 cipher 설정도 사용한다.
- 모듈: [common-core](../../modules/common-core/README.md), [stock-core](../../modules/stock-core/README.md).
- 설정: [application.yml](src/main/resources/application.yml), [공통 설정](../../modules/common-core/src/main/resources/application-common.yml).

전체 실행과 IDE 환경 변수는 [로컬 개발 안내](../../docs/operations/LOCAL_DEVELOPMENT.md)를 따른다. `DISCORD_TOKEN`, 개발 서버용 `DISCORD_DEV_GUILD_ID`, Redis·RabbitMQ 접속 값, 고유한 `APP_NODE_NAME`과 `HEALTH_PORT`를 확인한다.

## 응답 정책

| 명령 | 공개 범위 |
| --- | --- |
| `/man`, 음악·효과음, 주식 조회 | 본인에게만 표시 |
| `/stock buy`, `/stock sell`, `/stock rank`, `/pizza` | 채널에 공개 |

## 테스트와 점검

저장소 루트에서:

```bash
bash ./gradlew :apps:gateway-app:test
docker compose --env-file .env logs --tail=100 gateway
curl -fsS http://localhost:8081/actuator/health
```

단위 테스트는 명령 등록·공개 범위·결과 처리를 검사하며 실제 Discord 전달 성공은 별도 확인한다. `/actuator/info`, `/actuator/prometheus`도 노출한다.

[앱 목록](../README.md) · [메시지 계약](../../docs/reference/EVENT_CONTRACT.md) · [테스트 가이드](../../docs/operations/CI_TESTING.md)
