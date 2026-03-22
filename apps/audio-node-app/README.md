# audio-node-app

## 역할

`audio-node-app`은 명령을 실제로 수행하는 실행 노드다. RabbitMQ에서 command를 consume해서 음성 연결, 곡 로드, 재생, 복구, 유휴 퇴장을 수행하고, 완료 결과를 `MusicCommandResultEvent`로 다시 발행한다.

## 주요 패키지

### `discordgateway.audionode`

- `AudioNodeApplication`
  - audio-node-app Spring Boot 시작점

### `discordgateway.audionode.config`

- `AudioNodeComponentConfiguration`
  - audio-node 전용 bean 조립

### `discordgateway.audionode.recovery`

- `PlaybackRecoveryService`
  - Redis 상태 기준 복구 수행
- `PlaybackRecoveryReadyListener`
  - Ready 이후 recovery 시작

### `discordgateway.audionode.lifecycle`

- `VoiceChannelIdleDisconnectService`
  - 비어 있는 음성 채널 유휴 퇴장 예약 / 취소 / 실행
- `VoiceChannelIdleListener`
  - `GuildVoiceUpdateEvent` 감시

## 공용 코어 사용

실제 재생 코어는 `common-core`에 있다. audio-node-app은 그 코어를 실행하는 앱 계층이다.

주요 코어 클래스:

- `discordgateway.playback.application.MusicWorkerService`
- `discordgateway.playback.application.VoiceSessionLifecycleService`
- `discordgateway.playback.audio.PlayerManager`
- `discordgateway.playback.audio.TrackScheduler`
- `discordgateway.infra.audio.LavaPlayerPlaybackGateway`
- `discordgateway.infra.audio.JdaVoiceGateway`

## 동작 방식

1. RabbitMQ command 소비
2. `MusicWorkerService`로 전달
3. playback 코어 실행
4. Redis 상태 반영
5. `MusicCommandResultEvent` 발행
6. recovery / idle disconnect는 별도 lifecycle 경로로 실행
7. 내부 상태 변화는 Spring local event와 구조 로그로 남김

## 고정된 의존 경로

- command consumer: RabbitMQ
- command result publisher: RabbitMQ
- 상태 저장소: Redis
- 내부 상태 이벤트: Spring local event
- in-memory fallback: 없음
- in-process bus: 없음

## 설정

앱 전용 설정 파일:

- `src/main/resources/application.yml`

공통 설정은 `application-common.yml`을 사용한다.

유휴 퇴장 관련 설정:

- `ops.voice-idle-disconnect-enabled`
- `ops.voice-idle-timeout`

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
- `YOUTUBE_REFRESH_TOKEN`
- `YOUTUBE_PO_TOKEN`
- `YOUTUBE_VISITOR_DATA`
- `YOUTUBE_REMOTE_CIPHER_URL`
- `YOUTUBE_REMOTE_CIPHER_PASSWORD`
- `YOUTUBE_REMOTE_CIPHER_USER_AGENT`

## 빌드 / 실행

```powershell
.\gradlew.bat :apps:audio-node-app:bootJar
java -jar apps/audio-node-app/build/libs/audio-node-app.jar
```

또는

```powershell
.\gradlew.bat :apps:audio-node-app:bootRun
```

## 관측성

노출 endpoint:

- `/actuator/health`
- `/actuator/info`
- `/actuator/prometheus`

주요 로그:

- `startup-config`
- `music-command consume`
- `music-command duplicate replay`
- YouTube 로드 실패 로그
- recovery 시작 / 완료 로그
- voice idle disconnect 로그

## 확인 포인트

정상 기동 후 audio-node 로그에서 보통 아래가 보인다.

- `application=audio-node-app`
- `role=AUDIO_NODE`
- command result 발행 로그
