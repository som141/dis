# audio-node-app

## 역할

`audio-node-app`은 명령을 실제로 수행하는 실행 노드다. 재생, 음성 연결, 상태 전이, 복구는 모두 이 앱에서 시작된다.

현재 책임은 아래로 고정되어 있다.

- RabbitMQ command consumer
- 음성 채널 연결 및 해제
- 트랙 로드, 재생, 정지, 스킵
- Redis 상태 반영
- 기동 시 playback recovery
- Spring local event 발행

## 주요 클래스

### `discordgateway.audionode.AudioNodeApplication`

- audio-node-app의 Spring Boot 메인 클래스
- 최종 실행 JAR의 진입점

### `discordgateway.audionode.AudioNodeComponentConfiguration`

- audio-node 전용 bean 구성
- `RabbitMusicCommandListener`
- `PlaybackRecoveryService`
- `PlaybackRecoveryReadyListener`

### `discordgateway.infrastructure.messaging.rabbit.RabbitMusicCommandListener`

- RabbitMQ queue에서 `MusicCommandMessage` 소비
- command dedup
- 실패 시 DLQ 경계 처리
- `MusicWorkerService` 호출

### `discordgateway.application.PlaybackRecoveryService`

- Redis에 저장된 guild, player, queue 상태를 기준으로 복구 수행

### `discordgateway.discord.PlaybackRecoveryReadyListener`

- JDA Ready 이후 recovery 시작

## 실제 재생 엔진 위치

`audio-node-app` 자체는 실행 경계이고, 실제 재생 코어는 `common-core`에 있다.

중심 클래스:

- `MusicWorkerService`
- `PlayerManager`
- `TrackScheduler`
- `LavaPlayerPlaybackGateway`
- `JdaVoiceGateway`

즉 `audio-node-app`은 실행 노드, `common-core`는 재생 코어다.

## 고정된 설계

- command consumer: `RabbitMusicCommandListener`
- 상태 저장소: Redis만 사용
- 이벤트 transport: Spring local event
- in-memory fallback 없음
- in-process bus 없음

정상 기동 로그에서는 보통 아래가 보인다.

- `startup-config`
- `application=audio-node-app`
- `commandBus=none`

`commandBus=none`은 정상이다. audio-node는 producer가 아니라 consumer이기 때문이다.

## 설정

앱 전용 설정 파일:

- `src/main/resources/application.yml`

현재 앱 전용 값:

- `spring.application.name=audio-node-app`
- `app.node-name`

공통 값은 `application-common.yml`을 사용한다.

## 주요 환경변수

- `DISCORD_TOKEN`
- `TOKEN`
- `DISCORD_DEV_GUILD_ID`
- `RABBITMQ_HOST`
- `RABBITMQ_PORT`
- `RABBITMQ_USERNAME`
- `RABBITMQ_PASSWORD`
- `REDIS_HOST`
- `REDIS_PORT`
- `APP_NODE_NAME`
- `HEALTH_PORT`
- `YOUTUBE_REFRESH_TOKEN`
- `YOUTUBE_PO_TOKEN`
- `YOUTUBE_VISITOR_DATA`
- `YOUTUBE_REMOTE_CIPHER_URL`
- `YOUTUBE_REMOTE_CIPHER_PASSWORD`
- `YOUTUBE_REMOTE_CIPHER_USER_AGENT`

## 빌드와 실행

JAR 생성:

```powershell
.\gradlew.bat :apps:audio-node-app:bootJar
```

실행:

```powershell
java -jar apps/audio-node-app/build/libs/audio-node-app.jar
```

Gradle 실행:

```powershell
.\gradlew.bat :apps:audio-node-app:bootRun
```

## Docker

- Dockerfile: `apps/audio-node-app/Dockerfile`
- 기본 이미지 태그: `discord-audio-node:local`

## 관측성

Audio Node는 아래 endpoint를 노출한다.

- `/actuator/health`
- `/actuator/info`
- `/actuator/prometheus`

로그는 ECS JSON 형식으로 출력된다.

주요 구조 로그:

- `startup-config`
- `music-command`
- `music-event`
- YouTube 로드 실패 로그
- recovery 시작/완료 로그

## 운영 시 확인 포인트

### RabbitMQ 미연결

증상:

- gateway가 응답은 하지만 실제 재생이 시작되지 않음

확인 값:

- `RABBITMQ_HOST`
- `RABBITMQ_PORT`
- queue consumer 상태

### Redis 미연결

증상:

- recovery 실패
- actuator health에서 Redis down

확인 값:

- `REDIS_HOST`
- `REDIS_PORT`

### YouTube 재생 실패

증상:

- 검색은 되는데 실제 재생이 시작되지 않음
- `track.load.failed`
- `All clients failed to load the item`
- `403`

확인 값:

- `YOUTUBE_REFRESH_TOKEN`
- `YOUTUBE_PO_TOKEN`
- `YOUTUBE_VISITOR_DATA`
- `YOUTUBE_REMOTE_CIPHER_URL`

주의:

- 로컬 Docker에서는 재생되는데 원격 서버에서는 실패하는 경우가 있다.
- 현재까지의 운영 경험상 이 문제는 코드보다 서버 IP/ASN, YouTube anti-bot 응답 차이의 영향을 크게 받는다.
