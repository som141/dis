# gateway-app

## 역할

`gateway-app`은 Discord 사용자 요청의 진입점이다. 이 모듈은 실제 재생을 직접 수행하지 않고, slash command를 `MusicCommand`로 변환해서 RabbitMQ RPC로 전달한다.

## 주요 패키지

### `discordgateway.gateway`

- `GatewayApplication`
  - gateway-app의 Spring Boot 시작점

### `discordgateway.gateway.config`

- `GatewayComponentConfiguration`
  - gateway 전용 bean 조립

### `discordgateway.gateway.application`

- `MusicApplicationService`
  - Discord 요청을 command로 변환하는 facade
- `PlayAutocompleteService`
  - `/play` 자동완성 후보 조회

### `discordgateway.gateway.presentation.discord`

- `DiscordBotListener`
  - slash command 처리
- `DiscordCommandCatalog`
  - 명령 정의
- `DiscordCommandRegistrationListener`
  - Ready 이후 명령 등록

## 동작 방식

1. Discord interaction 수신
2. 입력 검증
3. `MusicCommand` 생성
4. `RabbitMusicCommandBus`로 RPC 전송
5. 응답을 interaction 응답 또는 follow-up 메시지로 정리

## 고정된 의존 경로

- command bus: RabbitMQ RPC
- 상태 저장소 직접 접근: 없음
- 이벤트 소비: 없음
- in-memory fallback: 없음

## 설정

앱 전용 설정 파일:

- `src/main/resources/application.yml`

공통 설정은 `modules/common-core/src/main/resources/application-common.yml`에서 읽는다.

## 주요 환경 변수

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

구조 로그 키:

- `startup-config`
- `music-command`
- `music-event`

## 확인 포인트

정상 기동 시 gateway 로그에서 보통 아래가 보인다.

- `application=gateway-app`
- `commandBus=RabbitMusicCommandBus`
