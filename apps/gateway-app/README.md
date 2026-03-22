# gateway-app

## 역할

`gateway-app`은 Discord 사용자 요청의 진입점이다. 이 모듈은 실제 재생을 직접 수행하지 않고, slash command를 `MusicCommandEnvelope`로 변환해서 RabbitMQ로 비동기 publish한다. 이후 audio-node가 발행한 `MusicCommandResultEvent`를 소비해서 original ephemeral reply를 수정한다.

## 주요 패키지

### `discordgateway.gateway`

- `GatewayApplication`
  - gateway-app Spring Boot 시작점

### `discordgateway.gateway.config`

- `GatewayComponentConfiguration`
  - gateway 전용 bean 조립
  - pending interaction 저장소
  - result queue / binding 선언

### `discordgateway.gateway.application`

- `MusicApplicationService`
  - Discord 요청을 `MusicCommandEnvelope`로 바꾸는 facade
- `PlayAutocompleteService`
  - `/play` 자동완성 조회

### `discordgateway.gateway.interaction`

- `InteractionResponseContext`
  - deferred interaction 응답 정보
- `PendingInteractionRepository`
  - `commandId -> InteractionResponseContext`
- `RedisPendingInteractionRepository`
  - Redis TTL 기반 구현

### `discordgateway.gateway.messaging`

- `RabbitMusicCommandResultListener`
  - audio-node가 발행한 command result event 소비
  - Discord original reply 수정

### `discordgateway.gateway.presentation.discord`

- `DiscordBotListener`
  - slash command 처리
  - `deferReply(true)` 수행
  - pending interaction 등록
- `DiscordCommandCatalog`
  - 명령 정의
- `DiscordCommandRegistrationListener`
  - Ready 이후 명령 등록

## 동작 방식

1. Discord interaction 수신
2. 입력 검증
3. `deferReply(true)` 수행
4. `MusicCommandEnvelope` 생성
5. `RabbitMusicCommandBus`로 command publish
6. `RabbitMusicCommandResultListener`가 result event 수신
7. original ephemeral reply 수정

## 고정된 의존 경로

- command bus: RabbitMQ async publish
- command result consumer: RabbitMQ
- 상태 저장소 직접 접근: 없음
- pending interaction 저장소: Redis
- 이벤트 소비 범위: command result 전용

## 설정

앱 전용 설정 파일:

- `src/main/resources/application.yml`

공통 설정은 `modules/common-core/src/main/resources/application-common.yml`에서 읽는다.

## 주요 환경변수

- `DISCORD_TOKEN`
- `TOKEN`
- `DISCORD_DEV_GUILD_ID`
- `RABBITMQ_HOST`
- `RABBITMQ_PORT`
- `RABBITMQ_USERNAME`
- `RABBITMQ_PASSWORD`
- `APP_NODE_NAME`
- `HEALTH_PORT`

## 빌드 / 실행

```powershell
.\gradlew.bat :apps:gateway-app:bootJar
java -jar apps/gateway-app/build/libs/gateway-app.jar
```

또는

```powershell
.\gradlew.bat :apps:gateway-app:bootRun
```

## 관측성

노출 endpoint:

- `/actuator/health`
- `/actuator/info`
- `/actuator/prometheus`

주요 로그:

- `startup-config`
- `music-command dispatch`
- `music-command result dropped`
- `music-command result reply edit failed`

## 확인 포인트

정상 기동 후 gateway 로그에서 보통 아래가 보인다.

- `application=gateway-app`
- `commandBus=RabbitMusicCommandBus`
- result queue 이름
