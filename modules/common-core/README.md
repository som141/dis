# common-core

## 역할

`common-core`는 별도 서비스가 아니라 두 실행 앱이 함께 쓰는 공용 라이브러리 모듈이다.

현재 이 모듈은 아래를 담당한다.

- command / event 모델
- worker 로직
- 재생 엔진
- Redis 저장소 구현
- RabbitMQ command 인프라
- 공통 Spring bootstrap

## 현재 구조

```text
modules/common-core/
  build.gradle
  README.md
  src/main/java/discordgateway/
    application/
    application/event/
    audio/
    bootstrap/
    domain/
    infrastructure/
  src/main/resources/
    application-common.yml
    logback.xml
    smbj.mp3
    gsuck.mp3
```

## 패키지별 책임

### `application`

- `MusicCommand`
- `MusicCommandBus`
- `MusicCommandMessage`
- `MusicWorkerService`
- `MusicCommandTraceContext`

### `application.event`

- `MusicEvent`
- `MusicEventFactory`
- `MusicEventPublisher`
- `SpringMusicEventPublisher`
- `MusicEventLogListener`

### `audio`

- `PlayerManager`
- `TrackScheduler`
- `GuildMusicManager`
- `AudioPlayerSendHandler`

### `bootstrap`

- `ApplicationFactory`
- `RabbitMessagingConfiguration`
- `AppProperties`
- `MessagingProperties`
- `DiscordProperties`
- `YouTubeProperties`
- `RedisConnectionProperties`

### `domain`

- `QueueEntry`
- `PlayerState`
- `GuildPlayerState`
- `QueueRepository`
- `PlayerStateRepository`
- `GuildStateRepository`
- `ProcessedCommandRepository`

### `infrastructure`

- Redis 구현
- JDA / Discord 구현
- 오디오 gateway 구현
- RabbitMQ command 구현

## 현재 고정 구조

이 모듈은 더 이상 선택형 저장소나 transport를 제공하지 않는다.

- 상태 저장소: Redis 고정
- command 전송: RabbitMQ 고정
- 이벤트 발행: Spring local event 고정
- InMemory 구현: 제거
- InProcess command bus: 제거
- Rabbit event outbox: 제거

즉 현재 common-core는 "현재 운영 구조만 남긴 공용 코어"다.

## 설정 파일

- [application-common.yml](src/main/resources/application-common.yml)

공통 파일에는 아래처럼 실제 공용값만 남아 있다.

- `discord.*`
- `server.port`
- `management.*`
- `messaging.rpc-timeout-ms`
- `messaging.command-*`
- `spring.rabbitmq.*`
- `ops.*`

앱별 값은 각 앱 모듈이 최종 결정한다.

예:

- `app.node-name`

## 빌드 역할

`common-core`는 독립 실행 JAR을 만들지 않는다.

최종 실행 산출물은 아래 앱 모듈에서 만든다.

- `apps/gateway-app`
- `apps/audio-node-app`

## 왜 공용 모듈을 유지하나

공용 모듈이 없으면 아래 둘 중 하나가 된다.

- 같은 코드를 두 앱에 복제
- 한 앱이 다른 앱 코드를 직접 참조

둘 다 유지보수성이 나쁘다. 그래서 현재 구조에서는 공용 코어를 명시적으로 두는 편이 맞다.
