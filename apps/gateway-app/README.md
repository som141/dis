# gateway-app

## 역할

`gateway-app`은 Discord slash command 진입점이다.

현재 책임은 아래로 고정되어 있다.

- Discord interaction 수신
- autocomplete 처리
- 입력 검증
- Discord 요청을 `MusicCommand`로 변환
- RabbitMQ command bus로 명령 전달
- 사용자에게 즉시 응답 반환

실제 재생, 큐 전이, recovery, 상태 저장은 이 앱이 하지 않는다. 그 책임은 `audio-node-app`과 `modules/common-core`에 있다.

## 현재 구조

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
- 최종 실행 JAR의 `main class`

### `GatewayComponentConfiguration`

- gateway 전용 bean 등록
- `MusicApplicationService`
- `PlayAutocompleteService`
- `RabbitMusicCommandBus`
- `DiscordBotListener`
- `DiscordCommandRegistrationListener`

### `MusicApplicationService`

- Discord 요청을 `MusicCommand`로 바꾸는 gateway facade
- command bus 호출만 담당

### `PlayAutocompleteService`

- `/play` autocomplete용 검색 후보 조회

### `DiscordBotListener`

- slash command 분기
- interaction 응답 처리
- follow-up 메시지 처리

### `DiscordCommandRegistrationListener`

- Ready 이후 slash command 등록

## 현재 고정 구조

gateway는 더 이상 선택형 transport를 쓰지 않는다.

- command 전송: `RabbitMusicCommandBus` 고정
- 이벤트 발행: Spring local event 고정
- 상태 저장소: Redis 고정
- 로컬 in-memory fallback: 없음
- in-process command bus: 없음

정상 기동 로그 예시는 아래와 같다.

- `startup-config application=gateway-app`
- `commandBus=RabbitMusicCommandBus`

## 설정 파일

파일:

- [application.yml](src/main/resources/application.yml)

현재 gateway가 직접 갖는 앱 설정은 최소값만 남아 있다.

- `app.node-name`

공통 설정은 `classpath:application-common.yml`에서 가져온다.

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

### Gradle 실행

```powershell
.\gradlew.bat :apps:gateway-app:bootRun
```

## Docker

- [Dockerfile](Dockerfile)

기본 로컬 이미지 태그:

- `discord-gateway:local`

## 장애 확인 포인트

### Discord 토큰 문제

증상:

- JDA 초기화 실패
- `Discord bot token is missing`

확인:

- `DISCORD_TOKEN`
- fallback `TOKEN`

### RabbitMQ 연결 문제

증상:

- slash command 응답은 오는데 실제 처리 결과가 돌아오지 않음
- RPC timeout

확인:

- `RABBITMQ_HOST`
- `RABBITMQ_PORT`
- `RABBITMQ_USERNAME`
- `RABBITMQ_PASSWORD`

### 구조 확인

gateway 로그에서 `commandBus=RabbitMusicCommandBus`가 보여야 정상이다.

## 수정 범위 기준

gateway에서 수정하는 것이 맞는 범위:

- Discord interaction UX
- slash command 입력 형식
- autocomplete 정책
- 즉시 응답 메시지

gateway에서 수정하면 안 되는 범위:

- 큐 저장 구조
- 재생 상태 전이
- recovery 규칙
- Redis 저장 로직
- Rabbit command consumer

## 관측성 확인

gateway-app은 공통 설정 기준으로 아래 Actuator endpoint를 노출한다.

- `/actuator/health`
- `/actuator/info`
- `/actuator/prometheus`

로그는 `stdout`으로 출력되고, `common-core`의 `logback-spring.xml`을 통해 ECS JSON 구조 로그로 기록된다.
