# audio-node-app · 음악 worker

음악 명령을 RabbitMQ에서 받아 음성 채널 연결·재생·대기열 변경을 실행한다. Redis 상태를 이용한 복구와 사용자가 없는 음성 채널의 자동 퇴장도 담당한다. 실제 재생 코어는 [common-core](../../modules/common-core/README.md)에 있다.

## 코드 읽기 순서

1. [AudioNodeApplication](src/main/java/discordgateway/audionode/AudioNodeApplication.java) → [AudioNodeComponentConfiguration](src/main/java/discordgateway/audionode/config/AudioNodeComponentConfiguration.java): 앱·consumer 조립.
2. [RabbitMusicCommandListener](../../modules/common-core/src/main/java/discordgateway/infra/messaging/rabbit/RabbitMusicCommandListener.java): 큐 수신, commandId 중복 확인, 결과 발행.
3. [MusicWorkerService](../../modules/common-core/src/main/java/discordgateway/playback/application/MusicWorkerService.java): 명령별 처리·음성 채널 조회·재생 요청.
4. [PlayerManager](../../modules/common-core/src/main/java/discordgateway/playback/audio/PlayerManager.java) → [TrackScheduler](../../modules/common-core/src/main/java/discordgateway/playback/audio/TrackScheduler.java) → [AudioPlayerSendHandler](../../modules/common-core/src/main/java/discordgateway/playback/audio/AudioPlayerSendHandler.java): 음원 로드·재생·Opus 프레임 공급.
5. [recovery/](src/main/java/discordgateway/audionode/recovery), [lifecycle/](src/main/java/discordgateway/audionode/lifecycle): 재시작 복구와 idle disconnect.

## 실행과 의존성

- Compose 서비스: `audio-node`, 호스트 HTTP `8082` → 컨테이너 `8080`.
- 연결: Discord 음성·Gateway API, RabbitMQ, Redis, 음원 서버, YouTube cipher.
- 설정: [application.yml](src/main/resources/application.yml), [공통 설정](../../modules/common-core/src/main/resources/application-common.yml), [YouTubeProperties](../../modules/common-core/src/main/java/discordgateway/common/bootstrap/YouTubeProperties.java).
- `DISCORD_TOKEN`, Redis·RabbitMQ 접속 값, `YOUTUBE_*` 설정을 사용한다. 전체 설명은 [로컬 개발 안내](../../docs/operations/LOCAL_DEVELOPMENT.md)에 있다.

`youtube-cipher`는 서명 해독만 맡는다. OAuth 초기화 성공·검색 성공·재생 요청 접수와 실제 음성 재생은 구분한다. 다운로드 실패나 프레임 정체는 후속 실패 결과와 로그로 확인한다. 기본 빈 채널 퇴장 대기 시간은 `ops.voice-idle-timeout=5s`다.

## 테스트와 점검

저장소 루트에서:

```bash
bash ./gradlew :modules:common-core:test
docker compose --env-file .env logs --since=5m --tail=200 audio-node youtube-cipher
curl -fsS http://localhost:8082/actuator/health
```

현재 음악 코어 테스트는 OAuth/cipher 설정과 재생 실패 통지 등을 검사한다. 실제 소리는 Discord에서 음성 채널 입장 → `/play` → `/pause`·`/resume` → `/stop`으로 확인한다. `/actuator/info`, `/actuator/prometheus`도 노출한다.

[앱 목록](../README.md) · [무음 장애 점검](../../docs/operations/OPERATIONS_RUNBOOK.md) · [cipher 이미지](../../ops/youtube-cipher/README.md) · [메시지 계약](../../docs/reference/EVENT_CONTRACT.md)
