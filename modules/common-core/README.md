# common-core · 음악 계약과 재생 코어

Gateway와 audio가 함께 사용하는 라이브러리다. 음악 메시지 계약, 재생 구현, Redis·RabbitMQ·JDA 연결과 공통 설정을 제공한다. 독립 실행 앱은 아니다.

## 수정할 위치

| 작업 | 코드 |
| --- | --- |
| 음악 명령·결과 계약 | [common/command/](src/main/java/discordgateway/common/command) |
| 플레이어 로드·대기열·실패 처리 | [playback/audio/](src/main/java/discordgateway/playback/audio) |
| worker 명령 처리 | [MusicWorkerService](src/main/java/discordgateway/playback/application/MusicWorkerService.java) |
| Redis 상태·잠금·중복 명령 저장 | [infra/redis/](src/main/java/discordgateway/infra/redis) |
| command/result 발행·소비·DLQ | [infra/messaging/rabbit/](src/main/java/discordgateway/infra/messaging/rabbit) |
| JDA·음성 연결 추상화 | [infra/audio/](src/main/java/discordgateway/infra/audio), [infra/discord/](src/main/java/discordgateway/infra/discord) |
| 공통 bean·설정 | [ApplicationFactory](src/main/java/discordgateway/common/bootstrap/ApplicationFactory.java), [application-common.yml](src/main/resources/application-common.yml) |

`MusicEvent`는 Spring 프로세스 내부 관측 이벤트다. Gateway로 보내는 RabbitMQ 결과는 `MusicCommandResultEvent`이므로 transport를 구분한다.

## 의존성과 경계

[build.gradle](build.gradle)에 JDA, LavaPlayer, youtube-source, DAVE native와 인프라 라이브러리가 선언돼 있다. YouTube 변경 대응은 OAuth·TV 클라이언트·remote cipher 관련 코드와 테스트를 함께 확인한다. 주식 업무 로직·영속화는 stock 앱이, 주식 계약은 [stock-core](../stock-core/README.md)가 담당한다.

음악 계약을 바꾸면 [Gateway](../../apps/gateway-app/README.md)의 발행/응답 처리와 [audio](../../apps/audio-node-app/README.md)의 소비 코드를 함께 확인한다.

저장소 루트에서 테스트:

```bash
bash ./gradlew :modules:common-core:test
```

[모듈 목록](../README.md) · [계약 설명](../../docs/reference/EVENT_CONTRACT.md) · [테스트 범위](../../docs/operations/CI_TESTING.md)
