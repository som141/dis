# audio-node-app

## 역할

`audio-node-app`은 실제 재생을 담당하는 실행 앱이다.

이 앱의 책임은 아래와 같다.

- RabbitMQ command 소비
- 음성 채널 연결과 해제
- 실제 트랙 로드와 재생
- 재생 상태 전이
- recovery
- 이벤트 발행

즉 사용자의 명령을 받는 쪽이 아니라, 명령을 실행하는 쪽이다.

## 현재 디렉터리 구조

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
- 독립 실행 JAR의 `main class`

### `AudioNodeComponentConfiguration`

- audio-node 전용 bean 등록
- `PlaybackRecoveryService`
- `PlaybackRecoveryReadyListener`

### `PlaybackRecoveryService`

- Redis에 저장된 guild 상태, player 상태, queue 상태를 읽어서 복구 시도
- 연결된 voice channel과 now playing 정보를 기준으로 복구
- now playing 복구 실패 시 큐에서 다음 항목을 이어서 복구

### `PlaybackRecoveryReadyListener`

- JDA Ready 이후 recovery 시작
- audio-node가 재기동되었을 때 재생 상태 복원 진입점

## 실제 재생은 어디서 일어나나

audio-node 앱은 실행 진입점만 따로 있을 뿐, 핵심 재생 엔진 자체는 `common-core`에 있다.

실제로 묶이는 주요 공용 클래스:

- `MusicWorkerService`
- `PlayerManager`
- `TrackScheduler`
- `LavaPlayerPlaybackGateway`
- `JdaVoiceGateway`
- `RabbitMusicCommandListener`

즉 audio-node-app은 “오디오 전용 실행 셸”이고, 엔진은 common-core에 있다.

## 명령 처리 흐름

1. gateway가 RabbitMQ로 `MusicCommand`를 발행한다.
2. audio-node가 `RabbitMusicCommandListener`로 command를 소비한다.
3. `MusicWorkerService`가 command 타입별 로직을 수행한다.
4. `PlaybackGateway`와 `VoiceGateway`를 통해 재생 또는 음성 연결을 조작한다.
5. `PlayerManager`와 `TrackScheduler`가 재생 전이를 관리한다.
6. 상태 변화는 Redis와 이벤트로 반영된다.

## 복구 흐름

1. audio-node가 시작된다.
2. JDA Ready가 완료된다.
3. `PlaybackRecoveryReadyListener`가 recovery를 시작한다.
4. `PlaybackRecoveryService`가 Redis에서 guild 상태와 player 상태를 읽는다.
5. voice channel 재연결 후 now playing 또는 queue 기준으로 복구를 시도한다.

## 설정 파일

파일:

- [application.yml](src/main/resources/application.yml)

현재 audio-node 기본값:

- `app.role=audio-node`
- `messaging.command-transport=rabbitmq`
- `messaging.event-transport=spring`
- 저장소는 Redis 기준

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

## YouTube 관련 주의점

이 앱은 실제로 YouTube 스트림을 해석하고 재생하기 때문에, YouTube 관련 변수의 영향을 직접 받는다.

특히 운영 환경에서는 아래 문제가 자주 생긴다.

- `403`
- `sig function` 추출 실패
- `All clients failed to load the item`

이때는 audio-node 쪽 로그를 먼저 봐야 한다.

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

### Gradle로 실행

```powershell
.\gradlew.bat :apps:audio-node-app:bootRun
```

## Docker

- [Dockerfile](Dockerfile)

audio-node는 독립 이미지로 빌드된다.

- 기본 로컬 태그: `discord-audio-node:local`

compose에서 `audio-node` 서비스는 이 이미지를 사용한다.

## 운영에서 볼 포인트

정상 시작 로그 예시:

- `startup-config role=AUDIO_NODE`
- `commandTransport=rabbitmq`
- `commandBus=RabbitMusicCommandBus`

여기서 `InProcessMusicCommandBus`가 나오면 구조가 잘못된 것이다.

## 장애 포인트

### 1. RabbitMQ 미연결

증상:

- gateway는 살아 있는데 실제 재생이 시작되지 않음
- command 소비 로그가 없음

확인:

- RabbitMQ 포트와 계정
- `commandTransport=rabbitmq`

### 2. JDA 음성 연결 문제

증상:

- `/join`은 성공 응답인데 실제 voice 연결이 안 됨

확인:

- guild voice state 권한
- 토큰과 JDA session
- native dave 로딩 로그

### 3. YouTube 스트림 로드 실패

증상:

- 검색은 되는데 소리가 안 남

확인:

- `YOUTUBE_REFRESH_TOKEN`
- `YOUTUBE_PO_TOKEN`
- `YOUTUBE_VISITOR_DATA`
- `YOUTUBE_REMOTE_CIPHER_URL`
- 서버 공인 IP / ASN 특성

## 이 앱을 수정할 때 기준

- recovery 규칙
- worker 실행 로직
- playback state transition
- queue polling 규칙
- 재생 이벤트 발행

이런 변경은 여기서 시작하거나, 실제 구현이 있는 common-core까지 같이 봐야 한다.
