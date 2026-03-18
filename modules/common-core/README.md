# common-core

## 역할

`common-core`는 런타임 서비스가 아니라, 두 앱이 함께 사용하는 공용 라이브러리 모듈이다.

이 모듈이 필요한 이유는 아래와 같다.

- gateway와 audio-node가 같은 command / event 모델을 써야 한다.
- Redis 저장 구조가 둘 사이에서 일치해야 한다.
- playback engine과 scheduler가 하나의 규칙으로 유지돼야 한다.
- Spring 공용 설정을 중복 없이 관리해야 한다.

즉 `세 번째 서버`가 아니라 `공유 코드 계층`이다.

## 현재 디렉터리 구조

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
    META-INF/persistence.xml
    smbj.mp3
    gsuck.mp3
  src/test/java/discordgateway/
```

## 패키지별 책임

### `application`

- command 모델
- command bus 인터페이스
- worker 서비스
- trace context

핵심 클래스:

- `MusicCommand`
- `MusicCommandBus`
- `MusicWorkerService`
- `MusicCommandMessage`
- `MusicCommandTraceContext`

### `application.event`

- 이벤트 모델
- 이벤트 팩토리
- 이벤트 퍼블리셔 인터페이스
- spring event bridge

핵심 클래스:

- `MusicEvent`
- `MusicEventFactory`
- `MusicEventPublisher`
- `SpringMusicEventPublisher`
- `CompositeMusicEventPublisher`

### `audio`

- 실제 재생 엔진
- scheduler
- guild별 music manager

핵심 클래스:

- `PlayerManager`
- `TrackScheduler`
- `GuildMusicManager`
- `AudioPlayerSendHandler`

### `bootstrap`

- 공통 Spring bean wiring
- 설정 프로퍼티
- RabbitMQ wiring
- health / ready lifecycle

핵심 클래스:

- `ApplicationFactory`
- `RabbitMessagingConfiguration`
- `AppProperties`
- `MessagingProperties`
- `DiscordProperties`
- `YouTubeProperties`

### `domain`

- 순수 도메인 모델과 저장 포트

핵심 타입:

- `QueueEntry`
- `PlayerState`
- `GuildPlayerState`
- `QueueRepository`
- `PlayerStateRepository`
- `GuildStateRepository`
- `ProcessedCommandRepository`

### `infrastructure`

- Redis 구현
- InMemory 구현
- Discord/JDA 구현
- Audio gateway 구현
- RabbitMQ 구현

## 이 모듈에 들어가야 하는 것

- 두 앱이 공통으로 쓰는 모델
- 두 앱이 공통으로 쓰는 infra
- 두 앱이 공통으로 써야 하는 wiring

## 이 모듈에 넣지 않는 것

- gateway에서만 쓰는 Discord listener 진입점
- audio-node에서만 쓰는 recovery 시작 트리거
- 앱별 `main class`
- 앱별 `application.yml`
- 앱별 Dockerfile

## 설정 파일

- [application-common.yml](src/main/resources/application-common.yml)

여기에는 앱 공통값만 둔다.

예:

- `discord.*`
- `server.port`
- `management.*`
- `messaging`의 공용 값
- `spring.rabbitmq.*`

반대로 앱마다 달라질 수 있는 값은 넣지 않는다.

예:

- `app.role`
- `messaging.command-transport`
- `messaging.event-transport`

이 값은 각 앱 모듈이 최종 결정한다.

## 빌드 역할

이 모듈은 독립 배포 JAR를 만들지 않는다.

대신 아래 앱들이 이 모듈을 의존한다.

- `apps/gateway-app`
- `apps/audio-node-app`

즉 최종 배포물은 앱 모듈에서 나오고, common-core는 그 안에 포함된다.

## 테스트 기준

공용 로직 테스트는 가능하면 여기 둔다.

현재 예시:

- queue repository 테스트
- player state repository 테스트

## common-core를 유지하는 이유

만약 이 모듈을 없애고 두 앱만 남기면 아래 중 하나가 된다.

- 공용 로직 복제
- 한 앱이 다른 앱 코드를 참조

둘 다 장기 유지보수에 불리하다. 그래서 현재 구조에서는 공용 코어를 명시적으로 남겨두는 편이 낫다.
