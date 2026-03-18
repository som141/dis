# gateway-app

## 역할

`gateway-app`은 Discord 사용자 요청이 가장 먼저 들어오는 진입점이다.

이 앱의 책임은 아래로 제한한다.

- slash command 수신
- autocomplete 처리
- 입력 검증
- Discord 객체를 command 모델로 변환
- command bus로 명령 전달
- 사용자에게 즉시 응답 반환

이 앱은 가능한 한 `얇게` 유지하는 것이 목표다. 실제 재생 로직, 큐 전이, recovery는 여기서 처리하지 않는다.

## 현재 디렉터리 구조

```text
apps/gateway-app/
  build.gradle
  Dockerfile
  README.md
  src/main/java/discordgateway/gateway/
    GatewayApplication.java
    GatewayComponentConfiguration.java
  src/main/java/discordgateway/application/
    MusicApplicationService.java
    PlayAutocompleteService.java
  src/main/java/discordgateway/discord/
    DiscordBotListener.java
    DiscordCommandCatalog.java
    DiscordCommandRegistrationListener.java
  src/main/resources/
    application.yml
```

## 핵심 클래스

### `GatewayApplication`

- gateway 앱의 Spring Boot 진입점
- 독립 실행 JAR의 `main class`

### `GatewayComponentConfiguration`

- gateway 전용 bean 등록
- `MusicApplicationService`
- `PlayAutocompleteService`
- `DiscordBotListener`
- `DiscordCommandRegistrationListener`

즉 공용 코어에 두지 않는, gateway 역할 전용 wiring을 담당한다.

### `MusicApplicationService`

- Discord/JDA 객체를 받아 command 모델로 변환
- `MusicCommandBus`에 전달
- gateway 계층에서 쓰기 좋은 형태의 얇은 facade

### `PlayAutocompleteService`

- `/play` autocomplete에 필요한 검색 후보를 조회
- 사용자 입력 빈도가 높아 캐시와 응답 속도가 중요함

### `DiscordBotListener`

- slash command 처리의 중심
- `/join`, `/play`, `/skip`, `/stop`, `/queue`, `/pause`, `/resume`, `/clear`, `/sfx`, `/pizza` 등 명령 분기
- Discord interaction 응답과 후속 메시지 흐름 관리

### `DiscordCommandRegistrationListener`

- 봇 Ready 시 slash command 등록
- 운영 서버에서 command catalog를 Discord에 반영

## 요청 처리 흐름

1. Discord 사용자가 slash command를 호출한다.
2. `DiscordBotListener`가 이벤트를 받는다.
3. guild, text channel, user, option 값을 검증한다.
4. `MusicApplicationService`가 이를 `MusicCommand`로 바꾼다.
5. `MusicCommandBus`가 RabbitMQ로 명령을 전달한다.
6. gateway는 즉시 응답을 완료한다.
7. 실제 재생/후속 상태 변경은 audio-node가 처리한다.

## 이 앱에서 보지 말아야 할 것

아래는 gateway에서 직접 결정하면 안 된다.

- 실제 다음 곡 선택
- 재생 상태 저장 규칙
- recovery 실행 규칙
- guild 재생 락
- outbox 재전송

이런 로직은 `common-core`와 `audio-node-app` 쪽 책임이다.

## 설정 파일

파일:

- [application.yml](src/main/resources/application.yml)

현재 gateway 기본값:

- `app.role=gateway`
- `messaging.command-transport=rabbitmq`
- `messaging.event-transport=spring`
- 상태 저장소는 Redis 기준

## 주요 환경변수

- `DISCORD_TOKEN`
- `DISCORD_DEV_GUILD_ID`
- `RABBITMQ_HOST`
- `RABBITMQ_PORT`
- `RABBITMQ_USERNAME`
- `RABBITMQ_PASSWORD`
- `REDIS_HOST`
- `REDIS_PORT`
- `APP_NODE_NAME`
- `HEALTH_PORT`

YouTube 관련 변수도 공용 코어 초기화 과정에서 읽지만, gateway는 기본적으로 `재생 실행 주체`가 아니라 command 진입점이다.

## 빌드와 실행

### JAR 생성

```powershell
.\gradlew.bat :apps:gateway-app:bootJar
```

산출물:

- `apps/gateway-app/build/libs/gateway-app.jar`

### 직접 실행

```powershell
java -jar apps/gateway-app/build/libs/gateway-app.jar
```

### Gradle로 실행

```powershell
.\gradlew.bat :apps:gateway-app:bootRun
```

## Docker

- [Dockerfile](Dockerfile)

gateway는 독립 이미지로 빌드된다.

- 기본 로컬 태그: `discord-gateway:local`

compose에서 `gateway` 서비스는 이 이미지를 사용한다.

## 운영에서 볼 포인트

정상 시작 로그 예시:

- `startup-config role=GATEWAY`
- `commandTransport=rabbitmq`
- `commandBus=RabbitMusicCommandBus`

이 값이 아니면 gateway가 잘못된 transport를 타고 있을 가능성이 크다.

## 장애 포인트

### 1. Discord 토큰 문제

증상:

- 부팅 시 JDA 초기화 실패
- `Discord bot token is missing`

확인:

- `DISCORD_TOKEN`
- 또는 fallback인 `TOKEN`

### 2. RabbitMQ 연결 문제

증상:

- 명령은 들어오지만 worker까지 전달되지 않음
- reply-to timeout

확인:

- `RABBITMQ_HOST`, `RABBITMQ_PORT`
- `commandTransport=rabbitmq`

### 3. gateway가 직접 재생하는 버그

정상 구조에서는 gateway는 command만 보내야 한다.

로그에서 아래가 보이면 구조가 잘못된 것일 수 있다.

- `commandBus=InProcessMusicCommandBus`

## 이 앱을 수정할 때 기준

- Discord interaction 계층 변경
- command 입력 형식 변경
- autocomplete UX 조정
- 즉시 응답 메시지 조정

이런 변경은 여기서 처리한다.

반대로 아래는 여기서 수정하지 않는 것이 원칙이다.

- 재생 알고리즘
- 큐 저장 규칙
- recovery 알고리즘
- Redis key 설계
- Rabbit command consumer 로직
