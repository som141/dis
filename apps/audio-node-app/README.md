# audio-node-app

## 역할

`audio-node-app`은 실제 명령 실행과 재생 상태 관리를 담당하는 실행 앱이다.

현재 책임은 아래로 고정되어 있다.

- RabbitMQ command 수신
- 음성 채널 연결과 해제
- 트랙 로드와 재생
- 재생 상태 전이
- playback recovery
- 로컬 Spring 이벤트 발행
- Redis 상태 저장

## 현재 구조

```text
apps/audio-node-app/
  build.gradle
  Dockerfile
  README.md
  src/main/java/discordgateway/audionode/
    AudioNodeApplication.java
    AudioNodeComponentConfiguration.java
  src/main/java/discordgateway/application/
    PlaybackRecoveryService.java
  src/main/java/discordgateway/discord/
    PlaybackRecoveryReadyListener.java
  src/main/resources/
    application.yml
```

## 핵심 클래스

### `AudioNodeApplication`

- audio-node 앱의 Spring Boot 진입점
- 최종 실행 JAR의 `main class`

### `AudioNodeComponentConfiguration`

- audio-node 전용 bean 등록
- `RabbitMusicCommandListener`
- `PlaybackRecoveryService`
- `PlaybackRecoveryReadyListener`

### `RabbitMusicCommandListener`

- RabbitMQ queue에서 `MusicCommandMessage` 수신
- command dedup과 DLQ 경계 처리
- `MusicWorkerService` 호출

### `PlaybackRecoveryService`

- Redis에 저장된 guild/player/queue 상태 기준 복구

### `PlaybackRecoveryReadyListener`

- JDA Ready 이후 recovery 시작

## 실제 재생 엔진 위치

audio-node가 실행 앱이지만, 실제 재생 엔진 구현은 `modules/common-core`에 있다.

핵심 공용 클래스:

- `MusicWorkerService`
- `PlayerManager`
- `TrackScheduler`
- `LavaPlayerPlaybackGateway`
- `JdaVoiceGateway`

즉 `audio-node-app`은 실행 경계이고, 재생 코어는 `common-core`다.

## 현재 고정 구조

audio-node는 더 이상 선택형 저장소나 transport를 쓰지 않는다.

- command 소비: `RabbitMusicCommandListener` 고정
- 이벤트 발행: Spring local event 고정
- 상태 저장소: Redis 고정
- 로컬 in-memory fallback: 없음
- in-process command bus: 없음

정상 기동 로그 예시는 아래와 같다.

- `startup-config application=audio-node-app`
- `commandBus=none`

`commandBus=none`은 정상이다. audio-node는 producer가 아니라 consumer이기 때문이다.

## 설정 파일

파일:

- [application.yml](src/main/resources/application.yml)

현재 audio-node가 직접 갖는 앱 설정은 최소값만 남아 있다.

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
- `YOUTUBE_REFRESH_TOKEN`
- `YOUTUBE_PO_TOKEN`
- `YOUTUBE_VISITOR_DATA`
- `YOUTUBE_REMOTE_CIPHER_URL`
- `YOUTUBE_REMOTE_CIPHER_PASSWORD`
- `YOUTUBE_REMOTE_CIPHER_USER_AGENT`

## 빌드와 실행

### JAR 생성

```powershell
.\gradlew.bat :apps:audio-node-app:bootJar
```

산출물:

- `apps/audio-node-app/build/libs/audio-node-app.jar`

### 직접 실행

```powershell
java -jar apps/audio-node-app/build/libs/audio-node-app.jar
```

### Gradle 실행

```powershell
.\gradlew.bat :apps:audio-node-app:bootRun
```

## Docker

- [Dockerfile](Dockerfile)

기본 로컬 이미지 태그:

- `discord-audio-node:local`

## 장애 확인 포인트

### RabbitMQ 미연결

증상:

- gateway는 응답했는데 실제 재생이 시작되지 않음

확인:

- `RABBITMQ_HOST`
- `RABBITMQ_PORT`
- queue 선언 상태

### Redis 미연결

증상:

- recovery 실패
- health 에서 redis down

확인:

- `REDIS_HOST`
- `REDIS_PORT`

### YouTube 스트림 로드 실패

증상:

- 검색은 되는데 재생이 되지 않음

확인:

- `YOUTUBE_REFRESH_TOKEN`
- `YOUTUBE_PO_TOKEN`
- `YOUTUBE_VISITOR_DATA`
- `YOUTUBE_REMOTE_CIPHER_URL`

## 수정 범위 기준

audio-node에서 수정하는 것이 맞는 범위:

- command consumer
- recovery 규칙
- 재생 상태 전이
- Redis 저장 경계
- YouTube 재생 설정

audio-node에서 수정하면 안 되는 범위:

- Discord interaction UX
- slash command catalog
- autocomplete UX

## 관측성 확인

audio-node-app은 공통 설정 기준으로 아래 Actuator endpoint를 노출한다.

- `/actuator/health`
- `/actuator/info`
- `/actuator/prometheus`

로그는 `stdout`으로 출력되고, `common-core`의 `logback-spring.xml`을 통해 ECS JSON 구조 로그로 기록된다.
