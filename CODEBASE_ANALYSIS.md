# 코드베이스 구조 분석

## 1. 프로젝트 개요

이 저장소는 Discord 음악 봇을 Spring Boot 기반 워커로 실행하는 코드베이스다. 현재 구조는 단일 코드베이스를 유지하면서도 `gateway` 역할과 `audio-node` 역할을 설정으로 분리할 수 있게 설계되어 있다.

현재 핵심 특징은 다음과 같다.

- Spring Boot가 애플리케이션 수명주기, 설정, 헬스체크를 담당
- Discord 명령 수신 계층과 실제 재생 처리 계층이 분리됨
- 길드 상태, 플레이어 상태, 큐 상태를 저장소 포트로 외부화
- 명령 transport와 이벤트 transport를 설정으로 전환 가능
- 명령과 이벤트 모두 버전 정보(`schemaVersion`)를 가짐
- 이벤트는 명령과 연결 가능한 `correlationId`를 가짐
- RabbitMQ command consumer는 멱등 처리와 DLQ를 가짐
- RabbitMQ event publisher는 publisher confirm / mandatory return / outbox 재전송 구조를 가짐
- Redis outbox는 claim/lease 기반 분산 소비 구조를 가짐
- Docker Compose 기준 `gateway`, `audio-node`, `redis`, `rabbitmq` 분리 실행 구성을 가짐
- GitHub Actions가 서버 `deploy.sh`를 호출해 릴리스 디렉터리 방식으로 배포할 수 있음

## 2. 현재 아키텍처 요약

### `app.role=all`

- 하나의 프로세스에서 gateway + audio-node 역할을 모두 수행
- 로컬 개발이나 단일 프로세스 운영에 적합

### `app.role=gateway`

- Discord slash command 수신
- command 등록
- `MusicApplicationService`를 통해 command bus로 명령 발행
- 실제 재생 처리와 복구는 수행하지 않음

### `app.role=audio-node`

- RabbitMQ command consumer를 통해 명령 수신
- `MusicWorkerService`로 실제 재생 처리
- onReady 복구 수행
- playback event 발행 담당
- outbox relay를 통해 실패 이벤트 재전송 담당

## 3. 주요 흐름

### 명령 흐름

1. Discord 사용자가 slash command를 호출
2. `DiscordBotListener`가 요청을 받음
3. `MusicApplicationService`가 Discord 요청을 `MusicCommand`로 변환
4. `MusicCommandBus`가 명령을 전달
5. `InProcessMusicCommandBus` 또는 `RabbitMusicCommandBus`가 transport를 담당
6. Worker 쪽에서 `MusicWorkerService`가 명령을 실행
7. 실행 결과는 `CommandResult`로 반환

### 재생 흐름

1. `MusicWorkerService`가 음성 연결, 큐 적재, 재생 제어를 요청
2. `PlaybackGateway`와 `VoiceGateway`가 실제 오디오/음성 연결을 처리
3. `PlayerManager`와 `TrackScheduler`가 곡 로딩, 재생 전이, autoplay, recovery를 처리
4. 재생 상태는 `PlayerStateRepository`, `GuildStateRepository`, `QueueRepository`를 통해 저장

### 이벤트 흐름

1. Worker 또는 scheduler에서 상태 변화가 발생
2. `MusicEventFactory`가 공통 메타데이터를 채워 `MusicEvent`를 생성
3. 기본적으로 Spring in-process 이벤트로 로깅/관측
4. `event-transport=rabbitmq`이면 RabbitMQ exchange로도 발행
5. broker confirm과 return을 확인
6. RabbitMQ 발행 실패 시 outbox에 저장
7. `MusicEventOutboxRelay`가 due 이벤트를 claim
8. claim한 노드만 성공 삭제 또는 실패 재예약을 수행

### 배포 흐름

1. GitHub Actions가 `bootJar`를 빌드
2. Docker 이미지를 `discord-bot:<git-sha>`로 생성
3. 이미지를 tar.gz로 저장해 서버로 업로드
4. 서버 `deploy.sh`가 `docker load`
5. 서버 `docker compose up -d --no-build --remove-orphans`
6. `gateway`, `audio-node`, `redis`, `rabbitmq`를 갱신

## 4. 패키지 구조

### `discordgateway`

- `Main`
  - Spring Boot 진입점
  - 스케줄링 활성화

### `discordgateway.bootstrap`

- `ApplicationFactory`
  - 저장소, 서비스, 게이트웨이, JDA, 이벤트 팩토리 등 핵심 빈 조립
- `RabbitMessagingConfiguration`
  - RabbitMQ command / event transport 구성
- `AppProperties`
  - 역할, 저장소 선택, 노드 이름 등 앱 레벨 설정
- `MessagingProperties`
  - command/event transport, exchange/queue, outbox 재전송, confirm timeout, claim TTL 설정
- `DiscordProperties`
  - Discord 토큰과 개발용 길드 설정
- `YouTubeProperties`
  - YouTube 관련 설정
- `RedisConnectionProperties`
  - Redis 연결 설정
- `ReadyFileLifecycle`
  - readiness 파일 lifecycle
- `DiscordGatewayHealthIndicator`
  - Actuator health 상태 제공

### `discordgateway.discord`

- `DiscordBotListener`
  - slash command / autocomplete 처리
- `DiscordCommandRegistrationListener`
  - onReady 시 명령 등록
- `PlaybackRecoveryReadyListener`
  - onReady 시 복구 실행

### `discordgateway.application`

- `MusicApplicationService`
  - Gateway service
- `MusicWorkerService`
  - Worker service
- `MusicCommand`
  - 명령 모델
- `MusicCommandMessage`
  - transport 공통 envelope
- `MusicCommandMessageFactory`
  - envelope 생성
- `MusicCommandBus`
  - 명령 전송 포트
- `InProcessMusicCommandBus`
  - 로컬 구현
- `MusicCommandTrace`
  - 명령 추적 메타데이터
- `MusicCommandTraceContext`
  - 비동기 callback까지 추적을 전파하는 컨텍스트
- `PlayAutocompleteService`
  - `/play` 자동완성 처리
- `PlaybackRecoveryService`
  - 재기동 복구 로직

### `discordgateway.application.event`

- `MusicEvent`
  - 이벤트 계약
- `MusicEventFactory`
  - 이벤트 생성기
- `MusicEventPublisher`
  - 이벤트 발행 포트
- `SpringMusicEventPublisher`
  - Spring in-process 이벤트 발행
- `CompositeMusicEventPublisher`
  - 여러 publisher를 묶는 조합기
- `MusicEventLogListener`
  - 이벤트 로그 관측

### `discordgateway.audio`

- `PlayerManager`
  - LavaPlayer 로드/재생 관리
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
  - 음성 연결 포트
- `JdaVoiceGateway`
  - JDA 구현

### `discordgateway.infrastructure.messaging.rabbit`

- `RabbitMusicCommandBus`
  - RabbitMQ RPC 명령 전송
- `RabbitMusicCommandListener`
  - RabbitMQ command consumer
- `RabbitMusicEventSender`
  - RabbitMQ 이벤트 직접 전송
  - publisher confirm / mandatory return 검증
- `RabbitMusicEventPublisher`
  - 즉시 발행 후 실패 시 outbox 저장
- `MusicEventOutboxRelay`
  - 스케줄링 기반 outbox claim / replay

### `discordgateway.domain`

- `GuildStateRepository`
- `PlayerStateRepository`
- `QueueRepository`
- `ProcessedCommandRepository`
- `MusicEventOutboxRepository`
- `GuildPlaybackLockManager`

도메인 포트는 구현 세부사항과 분리된 저장/락 계약을 제공한다.

### `discordgateway.infrastructure.memory`

- 메모리 기반 저장소 구현
- 개발 환경과 단일 프로세스 모드에 적합

### `discordgateway.infrastructure.redis`

- Redis 기반 저장소 구현
- 길드 상태, 큐 상태, 플레이어 상태, command dedup, event outbox를 저장 가능
- event outbox는 Lua 스크립트 기반 claim/lease 흐름을 가짐

## 5. 저장 구조

### 상태 저장

- `GuildStateRepository`
  - 길드의 음성 연결 상태
- `PlayerStateRepository`
  - nowPlaying, paused, autoplay 등 재생 상태
- `QueueRepository`
  - 대기열 source of truth

### 운영 안전장치 저장

- `ProcessedCommandRepository`
  - commandId 기준 멱등 처리
- `MusicEventOutboxRepository`
  - RabbitMQ 이벤트 발행 실패분 재전송 대기 저장
  - claimOwner / claimToken / claimUntil 기반 소유권 관리

## 6. 실행 구성

### 기본 구성

- `application.yml`
  - 단일 프로세스 기본 설정
  - `app.role=all`

### 분리 프로필

- `application-gateway.yml`
  - `gateway` 전용 설정
  - Redis 저장소 사용
  - RabbitMQ command bus 사용
- `application-audio-node.yml`
  - `audio-node` 전용 설정
  - Redis 저장소 사용
  - RabbitMQ command / event transport 사용

### Compose 구성

- `docker-compose.yml`
  - `redis`
  - `rabbitmq`
  - `gateway`
  - `audio-node`
- `.env.example`
  - 토큰과 RabbitMQ 계정 예시

### 배포 스크립트 구성

- `.github/workflows/cicd-deploy.yml`
  - GitHub Actions 배포 워크플로
- `deploy.sh`
  - 서버 릴리스 배포 스크립트
- `SERVER_DEPLOY_SCRIPT.md`
  - 서버 배포 스크립트 설명 문서

## 7. 현재 완료된 스테이지

- Stage 1: 계층 분리
- Stage 2: 길드 상태 외부화
- Stage 3: 큐 외부화
- Stage 4: 플레이어 상태 외부화
- Stage 5: 저장소 기반 큐 전이 + 길드 락
- Stage 6: 복구 로직
- Stage 7: Gateway / Worker 분리
- Stage 8: Spring Boot 워커 전환
- Stage 9: 이벤트 계약 추가
- Stage 10: RabbitMQ transport 도입
- Stage 11: schemaVersion / correlationId 추적
- Stage 12: 역할 기반 프로세스 분리
- Stage 13: command dedup + DLQ
- Stage 14: event outbox + 재전송
- Stage 15: publisher confirm + mandatory return
- Stage 16: outbox claim / lease
- Stage 17: gateway / audio-node profile + compose 배포 구성
- Stage 18: GitHub Actions + 서버 deploy.sh 배포 정렬

## 8. 현재 구조의 장점

- 포조 코어를 유지하면서도 스프링 워커 운영 기능을 활용할 수 있음
- 저장소 포트가 명확해서 메모리와 Redis 전환이 쉬움
- command와 event 경계를 코드로 고정해서 분리 배포에 유리함
- 단일 JAR로 시작하되 역할 분리 배포로 확장 가능함
- command 쪽은 멱등 처리, event 쪽은 confirm/return + outbox 재전송 + claim/lease로 운영 안정성이 높아짐
- GitHub Actions와 서버 쉘 스크립트가 현재 compose 기반 배포와 맞물리도록 정리됨

## 9. 원본 지침 md와의 차이

원본 기준 문서는 `discord_msa_codex_instructions.md`다.

현재 구조는 원본 지침과 비교하면 다음 차이가 있다.

- 원본 문서는 Stage 3부터 시작한다고 적혀 있었지만 실제 코드에는 Stage 3의 일부가 이미 선반영되어 있었다.
- 원본 문서는 `Gateway -> Worker -> Audio Node` 3단 구조를 직접적인 목표로 잡았지만, 현재 구현은 `Gateway 역할 + Audio Node 역할 + 내부 Worker 코어` 구조다.
- 원본 문서는 Observability를 Stage 8의 핵심 목표로 두었지만, 현재 구현은 그 전에 RabbitMQ 운영 안정성과 Redis 분산 안전장치를 더 깊게 확장했다.
- 원본 문서에 없던 추가 설계가 들어갔다.
  - Spring Boot 호스트 전환
  - event contract
  - `schemaVersion`, `correlationId`
  - command dedup / DLQ
  - event outbox
  - publisher confirm / mandatory return
  - claim / lease
  - 역할별 profile / compose 배포 구성
  - GitHub Actions + 서버 deploy.sh 배포 구성

## 10. 현재 남은 핵심 작업

- command DLQ 재처리 운영 절차와 도구 마련
- 실제 Discord 환경에서 이중 JDA 세션 운영 검증
- 필요하다면 `Gateway -> Worker -> Audio Node` 3프로세스 구조로 Worker를 더 분리할지 검토
