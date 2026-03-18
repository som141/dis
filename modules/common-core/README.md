# common-core

## 역할

`common-core`는 별도 서비스가 아니라 두 실행 앱이 공유하는 공용 라이브러리 모듈이다.

현재 포함하는 책임은 아래와 같다.

- command / event 모델
- worker 로직
- 재생 엔진
- Redis 상태 저장소
- RabbitMQ command 인프라
- 공통 Spring bootstrap
- 공통 observability 설정

## 디렉터리 구조

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
    logback-spring.xml
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
- `OperationsProperties`

### `domain`

- `QueueEntry`
- `GuildPlayerState`
- `PlayerState`
- `GuildStateRepository`
- `QueueRepository`
- `PlayerStateRepository`
- `ProcessedCommandRepository`
- `GuildPlaybackLockManager`

### `infrastructure`

- Redis 구현
- JDA / Discord 구현
- voice / playback 구현
- RabbitMQ command 구현

## 현재 고정된 설계

이 모듈은 더 이상 선택형 저장소나 transport를 제공하지 않는다.

- 상태 저장소: Redis 고정
- command 전달: RabbitMQ 고정
- 이벤트 발행: Spring local event 고정
- InMemory 구현: 제거
- InProcess command bus: 제거
- Rabbit event outbox: 제거

즉 `common-core`는 현재 운영 경로만 남긴 공용 코어다.

## 설정 파일

### `application-common.yml`

공통 런타임 설정을 담는다.

- Discord 토큰
- actuator endpoint
- Micrometer tags
- structured logging
- RabbitMQ command 설정
- DLQ replay 설정

### `logback-spring.xml`

공통 콘솔 로그 형식을 정의한다.

- ECS structured logging
- YouTube / LavaPlayer 로그 레벨

## 빌드 역할

`common-core`는 실행 JAR를 만들지 않는다. 최종 산출물은 아래 두 앱에서 만든다.

- `apps/gateway-app`
- `apps/audio-node-app`

## 왜 공용 모듈이 필요한가

현재 구조에서 공용 코어를 제거하면 아래 둘 중 하나가 된다.

- 공용 코드를 두 앱에 중복 복사
- 한 앱이 다른 앱 코드를 직접 참조

둘 다 유지보수성이 나쁘기 때문에, 현재 구조에서는 공용 코어를 모듈로 두는 편이 더 명확하다.
