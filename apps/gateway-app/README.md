# gateway-app

## 역할

`gateway-app`은 Discord 사용자 요청의 진입점이다. 이 앱은 실제 재생 엔진이 아니라 명령 수신과 변환, 즉시 응답에 집중한다.

현재 책임은 아래로 고정되어 있다.

- Discord slash command 수신
- Discord autocomplete 처리
- 사용자 입력 검증
- Discord 요청을 `MusicCommand`로 변환
- RabbitMQ RPC command producer
- Discord interaction 즉시 응답 및 follow-up 메시지 처리

`gateway-app`은 직접 상태를 저장하지 않는다. 실제 재생과 상태 전이는 `audio-node-app`과 `common-core`가 담당한다.

## 주요 클래스

### `discordgateway.gateway.GatewayApplication`

- gateway-app의 Spring Boot 메인 클래스
- 최종 실행 JAR의 진입점

### `discordgateway.gateway.GatewayComponentConfiguration`

- gateway 전용 bean 구성
- `MusicApplicationService`
- `PlayAutocompleteService`
- `RabbitMusicCommandBus`
- `DiscordBotListener`
- `DiscordCommandRegistrationListener`

### `discordgateway.application.MusicApplicationService`

- Discord interaction을 `MusicCommand`로 변환하는 facade
- 실제 비즈니스 처리는 하지 않고 command bus 호출만 수행

### `discordgateway.application.PlayAutocompleteService`

- `/play` autocomplete 후보 조회
- 검색 UX를 위한 경량 서비스

### `discordgateway.discord.DiscordBotListener`

- slash command 분기
- interaction 응답 처리
- 채널 follow-up 메시지 구성

### `discordgateway.discord.DiscordCommandRegistrationListener`

- Ready 이후 slash command 등록

## 고정된 설계

- command bus: `RabbitMusicCommandBus`
- 상태 저장소: Redis 사용 경로만 허용
- 이벤트 transport: Spring local event
- in-process fallback 없음
- in-memory fallback 없음

정상 기동 로그에서는 보통 아래가 보인다.

- `startup-config`
- `application=gateway-app`
- `commandBus=RabbitMusicCommandBus`

## 설정

앱 전용 설정 파일:

- `src/main/resources/application.yml`

현재 앱 전용 값은 최소화되어 있다.

- `spring.application.name=gateway-app`
- `app.node-name`

나머지 공통 설정은 `modules/common-core/src/main/resources/application-common.yml`에서 가져온다.

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

## 빌드와 실행

JAR 생성:

```powershell
.\gradlew.bat :apps:gateway-app:bootJar
```

실행:

```powershell
java -jar apps/gateway-app/build/libs/gateway-app.jar
```

Gradle 실행:

```powershell
.\gradlew.bat :apps:gateway-app:bootRun
```

## Docker

- Dockerfile: `apps/gateway-app/Dockerfile`
- 기본 이미지 태그: `discord-gateway:local`

## 관측성

Gateway는 아래 endpoint를 노출한다.

- `/actuator/health`
- `/actuator/info`
- `/actuator/prometheus`

로그는 `common-core`의 `logback-spring.xml` 설정을 공유하며 ECS JSON 형식으로 출력된다.

주요 구조 로그:

- `startup-config`
- `music-command`
- `music-event`

## 운영 시 확인 포인트

### Discord 토큰 문제

증상:

- JDA 초기화 실패
- `Discord bot token is missing`

확인 값:

- `DISCORD_TOKEN`
- fallback `TOKEN`

### RabbitMQ 연결 문제

증상:

- slash command 즉시 응답만 오고 실제 처리가 되지 않음
- RPC timeout

확인 값:

- `RABBITMQ_HOST`
- `RABBITMQ_PORT`
- `RABBITMQ_USERNAME`
- `RABBITMQ_PASSWORD`

### 구조 확인

gateway 로그에서 `commandBus=RabbitMusicCommandBus`가 보여야 정상이다.
