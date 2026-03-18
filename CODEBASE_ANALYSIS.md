# 코드베이스 구조 분석

## 1. 프로젝트 개요

이 프로젝트는 Discord 음악 봇을 Spring Boot 기반 워커 구조로 재정리한 코드베이스다.

현재 구현은 하나의 코드베이스와 하나의 JAR를 유지하면서 아래 역할로 분리 실행할 수 있다.

- `all`
  - 단일 프로세스 실행
- `gateway`
  - slash command 수신과 command 발행
- `audio-node`
  - RabbitMQ command 소비와 실제 재생 처리

핵심 목표는 아래와 같다.

- 상태를 메모리 밖으로 이동
- command / event 경계를 코드로 고정
- RabbitMQ 기반 분리 배포 준비
- Redis 기반 복구와 분산 안전장치 확보
- GitHub Actions와 서버 스크립트 기반 배포 정리

아키텍처 다이어그램과 컴포넌트별 특징, 워크로드 정리는 [CURRENT_ARCHITECTURE.md](C:/Users/s0302/OneDrive/바탕%20화면/portpolio/dis/CURRENT_ARCHITECTURE.md)에 별도로 정리했다.

## 2. 현재 아키텍처 요약

### Gateway

- Discord slash command 수신
- command 등록
- `MusicApplicationService`를 통해 command bus로 명령 전달

### Audio Node

- RabbitMQ command consumer
- `MusicWorkerService`를 통한 실제 재생 처리
- recovery 수행
- playback event 발행
- event outbox 재전송

### 공통 저장소

- `GuildStateRepository`
- `QueueRepository`
- `PlayerStateRepository`
- `ProcessedCommandRepository`
- `MusicEventOutboxRepository`

기본 구현은 memory / Redis 둘 다 존재하고, 역할별 profile에서는 Redis를 사용한다.

## 3. 주요 흐름

### 명령 흐름

1. Discord 사용자가 slash command 호출
2. `DiscordBotListener`가 요청 수신
3. `MusicApplicationService`가 `MusicCommand` 생성
4. `MusicCommandBus`가 command transport 수행
5. `MusicWorkerService`가 실제 작업 수행
6. 결과를 `CommandResult`로 반환

### 재생 흐름

1. Worker가 재생 / 정지 / 스킵 / 큐 조회 등을 요청
2. `PlaybackGateway`, `VoiceGateway`가 실제 오디오와 음성 채널 동작 수행
3. `PlayerManager`, `TrackScheduler`가 로드 / 다음 곡 / autoplay / recovery 처리
4. 상태는 Redis 또는 메모리 저장소에 기록

### 이벤트 흐름

1. Worker 또는 scheduler에서 상태 변화 발생
2. `MusicEventFactory`가 공통 메타데이터를 채워 `MusicEvent` 생성
3. 기본적으로 Spring 이벤트로 로컬 발행
4. `event-transport=rabbitmq`이면 RabbitMQ exchange로도 발행
5. publish 실패 시 outbox 저장
6. `MusicEventOutboxRelay`가 claim / lease 기반으로 재전송

### 배포 흐름

1. GitHub Actions가 `bootJar` 빌드
2. Docker 이미지 `discord-bot:<git-sha>` 생성
3. 이미지 tar.gz와 compose / env / 운영 스크립트를 서버로 업로드
4. 서버 `deploy.sh`가 릴리스 디렉터리 생성
5. `docker load`
6. `docker compose up -d --no-build --remove-orphans`

## 4. 패키지 구조

### `discordgateway`

- `Main`
  - Spring Boot 진입점
  - 스케줄링 활성화

### `discordgateway.bootstrap`

- `ApplicationFactory`
  - 저장소, 서비스, JDA, 게이트웨이 등 핵심 빈 조립
- `RabbitMessagingConfiguration`
  - RabbitMQ command / event transport 구성
- `AppProperties`
  - 역할, 저장소 선택, 노드 이름 등 설정
- `MessagingProperties`
  - exchange / queue / DLQ / outbox / confirm 관련 설정
- `OperationsProperties`
  - command DLQ 재처리 운영 모드 설정
- `DiscordProperties`
  - Discord 토큰과 개발용 길드 설정
- `RedisConnectionProperties`
  - Redis 연결 설정
- `DiscordGatewayHealthIndicator`
  - JDA 상태 health 반영
- `RedisStorageHealthIndicator`
  - Redis 상태 health 반영
- `ReadyFileLifecycle`
  - readiness 파일 lifecycle

### `discordgateway.discord`

- `DiscordBotListener`
  - slash command / autocomplete 처리
- `DiscordCommandRegistrationListener`
  - gateway ready 시 command 등록
- `PlaybackRecoveryReadyListener`
  - audio-node ready 시 recovery 수행

### `discordgateway.application`

- `MusicApplicationService`
  - Gateway service
- `MusicWorkerService`
  - Worker service
- `MusicCommand`
  - command 모델
- `MusicCommandMessage`
  - transport용 envelope
- `MusicCommandMessageFactory`
  - command envelope 생성
- `MusicCommandBus`
  - command 전송 포트
- `InProcessMusicCommandBus`
  - 인프로세스 구현
- `MusicCommandTrace`
  - 추적 메타데이터
- `MusicCommandTraceContext`
  - 비동기 callback까지 trace 전파
- `PlaybackRecoveryService`
  - 재기동 복구 로직

### `discordgateway.application.event`

- `MusicEvent`
  - event 계약
- `MusicEventFactory`
  - event 생성기
- `MusicEventPublisher`
  - event 발행 포트
- `SpringMusicEventPublisher`
  - Spring 이벤트 발행
- `CompositeMusicEventPublisher`
  - 여러 publisher 조합
- `MusicEventLogListener`
  - 이벤트 로그 관측

### `discordgateway.audio`

- `PlayerManager`
  - LavaPlayer 로드 / 재생 관리
- `GuildMusicManager`
  - 길드별 플레이어 보관
- `TrackScheduler`
  - 다음 곡 선택, autoplay, recovery, 상태 전이

### `discordgateway.infrastructure.audio`

- `PlaybackGateway`
  - 재생 제어 포트
- `LavaPlayerPlaybackGateway`
  - LavaPlayer 구현
- `VoiceGateway`
  - 음성 채널 연결 포트
- `JdaVoiceGateway`
  - JDA 구현

### `discordgateway.infrastructure.messaging.rabbit`

- `RabbitMusicCommandBus`
  - RabbitMQ RPC command 전송
- `RabbitMusicCommandListener`
  - RabbitMQ command consumer
- `RabbitMusicEventSender`
  - event 직접 발행
- `RabbitMusicEventPublisher`
  - 즉시 발행 후 실패 시 outbox 저장
- `MusicEventOutboxRelay`
  - outbox 재전송
- `CommandDlqReplayService`
  - command DLQ 재처리
- `CommandDlqReplayReport`
  - 재처리 결과 요약

### `discordgateway.domain`

- `GuildStateRepository`
- `QueueRepository`
- `PlayerStateRepository`
- `ProcessedCommandRepository`
- `MusicEventOutboxRepository`
- `GuildPlaybackLockManager`

### `discordgateway.infrastructure.memory`

- 메모리 기반 저장소 구현

### `discordgateway.infrastructure.redis`

- Redis 기반 저장소 구현
- queue, player state, guild state, command dedup, event outbox 저장
- outbox는 Lua 스크립트 기반 claim / lease 지원

## 5. 메시징 안정성 구조

### Command

- `MusicCommandMessage`에 `schemaVersion` 포함
- `commandId` 기준 멱등 처리
- 실패 시 DLQ 전송
- 운영 모드에서 DLQ 재처리 가능

### Event

- `MusicEvent`에 `schemaVersion`, `correlationId` 포함
- RabbitMQ publish confirm / return 확인
- 실패 시 outbox 저장
- claim / lease 기반 재전송

## 6. 실행 구성

### 기본 설정

- [application.yml](C:/Users/s0302/OneDrive/바탕%20화면/portpolio/dis/src/main/resources/application.yml)
  - `app.role=all`
  - in-process command
  - Spring event transport

### 역할별 profile

- [application-gateway.yml](C:/Users/s0302/OneDrive/바탕%20화면/portpolio/dis/src/main/resources/application-gateway.yml)
  - gateway 전용
  - Redis 사용
  - RabbitMQ command bus 사용
- [application-audio-node.yml](C:/Users/s0302/OneDrive/바탕%20화면/portpolio/dis/src/main/resources/application-audio-node.yml)
  - audio-node 전용
  - Redis 사용
  - RabbitMQ command / event transport 사용

### 운영 모드

- `ops.command-dlq-replay-enabled=true`
  - command DLQ 재처리 전용 모드
- 이 모드에서는 JDA, command listener, outbox relay를 비활성화

## 7. 배포 구성

### Compose

- [docker-compose.yml](C:/Users/s0302/OneDrive/바탕%20화면/portpolio/dis/docker-compose.yml)
  - `redis`
  - `rabbitmq`
  - `gateway`
  - `audio-node`

### GitHub Actions

- [cicd-deploy.yml](C:/Users/s0302/OneDrive/바탕%20화면/portpolio/dis/.github/workflows/cicd-deploy.yml)
  - `main` push 시 자동 배포
  - 이미지 tar.gz 업로드
  - compose / env / deploy script / ops scripts 업로드

### 서버 스크립트

- [deploy.sh](C:/Users/s0302/OneDrive/바탕%20화면/portpolio/dis/deploy.sh)
  - 릴리스 디렉터리 생성
  - 이전 릴리스 중지
  - 이미지 load
  - 고정 Compose 프로젝트 이름 기준 compose 갱신
- [ops/replay-command-dlq.sh](C:/Users/s0302/OneDrive/바탕%20화면/portpolio/dis/ops/replay-command-dlq.sh)
  - command DLQ 재처리
- [ops/smoke-check.sh](C:/Users/s0302/OneDrive/바탕%20화면/portpolio/dis/ops/smoke-check.sh)
  - 배포 직후 상태 확인

## 8. 현재 구조의 장점

- 포조 코어를 유지하면서 Spring Boot 운영 기능을 활용
- 상태 저장소와 transport를 설정으로 교체 가능
- command와 event 경계가 코드로 고정되어 분리 배포에 유리
- event 쪽은 confirm / outbox / claim / lease까지 갖춰 운영 안정성이 높음
- 배포 후 운영 보조 스크립트까지 코드베이스 안에 포함됨

## 9. 원본 지시 md와의 차이

기준 문서는 `discord_msa_codex_instructions.md`다.

차이는 아래와 같다.

- 원본은 Stage 3부터 시작한다고 가정했지만 실제 코드에는 Stage 3 수준 작업이 이미 들어가 있었음
- 원본은 완전한 `Gateway -> Worker -> Audio Node` 3프로세스 분리를 강하게 상정했지만 현재 구현은 `gateway 역할 + audio-node 역할 + 내부 worker core` 구조
- 원본보다 더 깊게 들어간 부분이 있음
  - event contract
  - `schemaVersion`, `correlationId`
  - command dedup / DLQ
  - event outbox
  - publisher confirm / return
  - outbox claim / lease
  - command DLQ 재처리 운영 모드
  - 배포 후 스모크 체크 스크립트

## 10. 현재 남은 핵심 작업

- 실제 Discord 운영 환경에서 이중 JDA 세션 검증
- command DLQ 재처리 빈도와 실패 패턴 관찰
- observability 스택 추가 여부 판단
- 필요 시 완전한 3프로세스 분리 검토
