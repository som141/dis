# common-core

## 역할

`common-core`는 별도 실행 앱이 아니라 두 실행 앱이 공유하는 공용 라이브러리 모듈이다. 공용 계약, playback 코어, Redis / RabbitMQ / JDA 인프라, 공통 bootstrap을 제공한다.

## 패키지 구조

```text
src/main/java/discordgateway/
  common/
    bootstrap/
    command/
    event/
  playback/
    application/
    audio/
    domain/
  infra/
    audio/
    discord/
    messaging/rabbit/
    redis/
```

## 패키지별 책임

### `discordgateway.common.bootstrap`

- `ApplicationFactory`
- `RabbitMessagingConfiguration`
- `AppProperties`
- `MessagingProperties`
- `DiscordProperties`
- `YouTubeProperties`
- `RedisConnectionProperties`
- `OperationsProperties`

### `discordgateway.common.command`

- `MusicCommand`
- `MusicCommandMessage`
- `MusicCommandEnvelope`
- `MusicCommandBus`
- `CommandDispatchAck`
- `CommandResult`
- `MusicCommandResultEvent`
- `MusicCommandTrace`
- `MusicCommandTraceContext`
- `DiscordReferenceResolver`

### `discordgateway.common.event`

- `MusicEvent`
- `MusicEventFactory`
- `MusicEventPublisher`
- `SpringMusicEventPublisher`
- `MusicEventLogListener`

### `discordgateway.playback.application`

- `MusicWorkerService`
- `VoiceSessionLifecycleService`

### `discordgateway.playback.audio`

- `PlayerManager`
- `TrackScheduler`
- `GuildMusicManager`
- `AudioPlayerSendHandler`

### `discordgateway.playback.domain`

- `GuildPlayerState`
- `PlayerState`
- `QueueEntry`
- repository port
- lock / processed command port

### `discordgateway.infra.*`

- Redis 구현
- RabbitMQ command / result 구현
- Discord/JDA 구현
- playback gateway 구현

## 현재 고정된 경로

- 상태 저장소: Redis 고정
- command transport: RabbitMQ async publish
- command result transport: RabbitMQ direct exchange
- 내부 상태 이벤트: Spring local event
- InMemory 구현: 제거
- InProcess command bus: 제거

## 리소스

- `application-common.yml`
  - 공통 애플리케이션 설정
- `logback-spring.xml`
  - ECS JSON structured logging

## 빌드 역할

`common-core`는 단독 실행 JAR를 만들지 않는다. 최종 실행 산출물은 아래 두 앱이 만든다.

- `apps/gateway-app`
- `apps/audio-node-app`

## 왜 공용 모듈이 필요한가

현재 구조에서 공용 코어가 없으면 아래 둘 중 하나가 된다.

1. 공용 코드를 두 앱에 중복 복사
2. 한 앱이 다른 앱 코드를 직접 참조

둘 다 유지보수가 나쁘기 때문에, 현재 구조에서는 공용 코어를 별도 모듈로 두는 편이 더 명확하다.
