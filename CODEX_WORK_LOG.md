# Codex 작업 로그

## 1. 작업 기준

- 사용자 요청: 코드 품질 중심으로 단계별 작업 계속 진행
- 테스트 실행은 사용자 측에서 수행
- 작업 로그는 스테이지별로 분리
- 문서는 한글로 작성

## 2. Stage 1

### 상태

- 기존 코드 기준 완료로 판단

### 확인 내용

- Listener, Application, Infrastructure 책임이 기본적으로 분리되어 있었음

## 3. Stage 2

### 상태

- 기존 코드 기준 완료로 판단

### 확인 내용

- 길드 상태를 저장소 포트로 분리한 구조가 이미 들어가 있었음

## 4. Stage 3

### 상태

- 기존 코드 기준 완료로 판단

### 확인 내용

- `QueueRepository` 기반 큐 외부화가 이미 반영되어 있었음
- 실제 Codex 작업 시작점은 Stage 4부터로 판단

## 5. Stage 4

### 추가 요소

- `PlayerState`
- `PlayerStateRepository`
- `InMemoryPlayerStateRepository`
- `RedisPlayerStateRepository`

### 반영 내용

- 플레이어 상태를 저장소로 분리
- pause / resume / stop / autoplay 관련 상태를 외부 저장소에 보관 가능하게 정리

## 6. Stage 5

### 추가 요소

- `GuildPlaybackLockManager`
- `InMemoryGuildPlaybackLockManager`
- `RedisGuildPlaybackLockManager`

### 반영 내용

- 다음 곡 결정 기준을 로컬 메모리 대신 `QueueRepository`로 전환
- `skip`, `stop`, `clear`가 저장소 기준으로 동작하도록 변경
- 길드 단위 락을 추가해 재생 전이 경쟁 상태를 줄임

## 7. Stage 6

### 추가 요소

- `PlaybackRecoveryService`
- `PlaybackRecoveryReadyListener`

### 반영 내용

- 프로세스 재기동 시 저장된 음성 채널과 재생 상태 기준으로 복구 수행
- `nowPlaying` 우선 복구 후 실패 시 큐 기준 복구로 이어지도록 정리

## 8. Stage 7

### 추가 요소

- `MusicCommand`
- `MusicCommandBus`
- `InProcessMusicCommandBus`
- `MusicWorkerService`
- `DiscordReferenceResolver`
- `JdaRuntimeContext`
- `JdaDiscordReferenceResolver`

### 반영 내용

- Gateway 역할과 Worker 역할을 코드 구조상 분리
- `MusicApplicationService`는 명령 발행만 담당
- 실제 재생 처리 로직은 `MusicWorkerService`로 이동

## 9. Stage 8

### 추가 요소

- `application.yml`
- `AppProperties`
- `DiscordProperties`
- `YouTubeProperties`
- `RedisConnectionProperties`
- `ReadyFileLifecycle`
- `DiscordGatewayHealthIndicator`

### 크게 수정한 요소

- `build.gradle`
- `Main`
- `ApplicationFactory`
- `RedisSupport`
- `PlayerManager`
- `TrackScheduler`
- `LavaPlayerPlaybackGateway`

### 반영 내용

- Spring Boot 기반 워커 호스트로 전환
- 수동 부트스트랩 코드를 Spring Bean 조립 방식으로 변경
- health / ready lifecycle을 Spring 방식으로 정리

## 10. Stage 9

### 추가 요소

- `MusicEvent`
- `MusicEventPublisher`
- `MusicEventFactory`
- `SpringMusicEventPublisher`
- `MusicEventLogListener`
- `EVENT_CONTRACT.md`

### 반영 내용

- Worker와 Audio Node 사이 공통 이벤트 계약을 코드로 고정
- 상태 변화를 이벤트로 남기도록 정리
- 이벤트 정의 방식을 별도 문서로 기록

## 11. Stage 10

### 추가 요소

- `MessagingProperties`
- `RabbitMessagingConfiguration`
- `MusicCommandMessage`
- `MusicCommandMessageFactory`
- `CompositeMusicEventPublisher`
- `RabbitMusicCommandBus`
- `RabbitMusicCommandListener`
- `RabbitMusicEventPublisher`

### 반영 내용

- 명령 transport를 `in-process` 또는 `rabbitmq`로 전환 가능하게 구성
- RabbitMQ RPC 기반 command bus 추가
- event publisher도 Spring / RabbitMQ 조합으로 전환 가능하게 구성

## 12. Stage 11

### 추가 요소

- `MusicProtocol`
- `MusicCommandTrace`
- `MusicCommandTraceContext`

### 반영 내용

- command envelope와 event 계약에 `schemaVersion`을 추가
- 이벤트에 `correlationId`를 추가
- 비동기 callback까지 같은 명령 추적 ID가 전파되도록 정리

## 13. Stage 12

### 추가 요소

- `AppRole`
- `ConditionalOnAppRole`
- `AppRoleCondition`
- `DiscordCommandRegistrationListener`

### 크게 수정한 요소

- `ApplicationFactory`
- `RabbitMessagingConfiguration`
- `DiscordBotListener`
- `application.yml`

### 반영 내용

- `app.role=all|gateway|audio-node` 역할 분리 도입
- Slash command 처리와 command 등록은 gateway 책임으로 분리
- recovery와 RabbitMQ command 소비는 audio-node 책임으로 분리

## 14. Stage 13

### 추가 요소

- `ProcessedCommand`
- `CommandProcessingStatus`
- `ProcessedCommandRepository`
- `InMemoryProcessedCommandRepository`
- `RedisProcessedCommandRepository`

### 반영 내용

- RabbitMQ command consumer에 멱등 처리 저장소 추가
- `commandId` 기준 중복 명령 차단
- command DLQ 구성 추가

## 15. Stage 14

### 추가 요소

- `MusicEventOutboxRepository`
- `PendingMusicEvent`
- `InMemoryMusicEventOutboxRepository`
- `RedisMusicEventOutboxRepository`
- `RabbitMusicEventSender`
- `MusicEventOutboxRelay`

### 크게 수정한 요소

- `RabbitMusicEventPublisher`
- `RabbitMessagingConfiguration`
- `ApplicationFactory`
- `AppProperties`
- `MessagingProperties`
- `application.yml`
- `Main`

### 반영 내용

- RabbitMQ event 발행 경로를 `즉시 발행 -> 실패 시 outbox 저장 -> 스케줄러 재전송` 구조로 보강
- outbox 저장소를 메모리 / Redis 중 선택 가능하게 연결

## 16. Stage 15

### 크게 수정한 요소

- `RabbitMusicEventSender`
- `MessagingProperties`
- `application.yml`

### 반영 내용

- RabbitMQ event 발행 후 publisher confirm을 기다리도록 변경
- nack / confirm timeout / mandatory return을 모두 실패로 간주

## 17. Stage 16

### 크게 수정한 요소

- `MusicEventOutboxRepository`
- `PendingMusicEvent`
- `InMemoryMusicEventOutboxRepository`
- `RedisMusicEventOutboxRepository`
- `MusicEventOutboxRelay`
- `MessagingProperties`
- `application.yml`

### 반영 내용

- outbox 계약을 `poll` 방식에서 `claim / lease` 방식으로 변경
- Redis에서는 Lua 스크립트로 claim, 성공 삭제, 실패 재예약을 원자 처리

## 18. Stage 17

### 추가 요소

- `.env.example`
- `application-gateway.yml`
- `application-audio-node.yml`

### 크게 수정한 요소

- `docker-compose.yml`
- `README.md`

### 반영 내용

- `gateway`, `audio-node`, `redis`, `rabbitmq`를 함께 띄우는 compose 구성 추가
- 역할별 Spring profile과 Redis / RabbitMQ 설정 정리

## 19. Stage 18

### 추가 요소

- `deploy.sh`
- `SERVER_DEPLOY_SCRIPT.md`

### 크게 수정한 요소

- `.github/workflows/cicd-deploy.yml`
- `docker-compose.yml`

### 반영 내용

- GitHub Actions 배포 워크플로를 현재 구조에 맞게 수정
- `bootJar` 기준 이미지 빌드와 서버 배포 스크립트 흐름 정리
- 서버 릴리스 디렉터리 방식 배포 도입

## 20. Stage 19

### 추가 요소

- `OperationsProperties`
- `CommandDlqReplayService`
- `CommandDlqReplayReport`
- `CommandDlqReplayRunner`
- `RedisStorageHealthIndicator`
- `ops/replay-command-dlq.sh`
- `ops/smoke-check.sh`
- `OPERATIONS_RUNBOOK.md`

### 크게 수정한 요소

- `build.gradle`
- `ApplicationFactory`
- `RabbitMessagingConfiguration`
- `application.yml`
- `.github/workflows/cicd-deploy.yml`
- `deploy.sh`
- `README.md`
- `SERVER_DEPLOY_SCRIPT.md`

### 반영 내용

- Windows 로컬 실행과 Alpine Linux 배포 이미지를 모두 고려해 DAVE native 런타임 의존성을 함께 정리
- command DLQ 재처리 전용 운영 모드를 추가
- 재처리 모드에서는 JDA, command consumer, outbox relay가 같이 뜨지 않도록 조건 분리
- 서버에서 바로 실행할 수 있는 DLQ 재처리 스크립트와 스모크 체크 스크립트 추가
- Redis 상태를 actuator health에 포함하도록 보강
- 운영 절차를 별도 런북으로 정리

## 21. 검증

### 실행한 검증

- `.\gradlew.bat compileJava compileTestJava`
  - 성공
- `.\gradlew.bat bootJar`
  - 성공

### 실행하지 않은 검증

- `.\gradlew.bat test`
  - 사용자 요청에 따라 실행하지 않음
- `docker compose up`
  - 실제 컨테이너 기동은 실행하지 않음
- GitHub Actions 실제 배포
  - 로컬에서 실행하지 않음
- 실제 Discord 환경 검증
  - 운영 환경 필요

## 22. 원본 지시 md 대비 진행률

기준 문서:

- `discord_msa_codex_instructions.md`

현재 판단:

- 원본 문서 기준 큰 단계는 사실상 `7 / 8` 수준까지 진행
- 비율로 보면 약 `87.5%`

## 23. 원본 문서와 달라진 점

- 원본 문서는 Stage 3부터 시작한다고 가정했지만 실제 코드에는 Stage 3 수준 작업이 이미 들어가 있었음
- 원본 문서는 `Gateway -> Worker -> Audio Node` 완전 3프로세스 분리를 직접 목표로 잡았지만 현재 구현은 `Spring Boot 단일 코드베이스 + gateway 역할 + audio-node 역할 + 내부 worker core` 구조
- 원본 문서에는 없던 추가 구현이 많이 들어감
  - Spring Boot 워커화
  - event contract
  - `schemaVersion`, `correlationId`
  - command dedup / DLQ
  - event outbox
  - publisher confirm / mandatory return
  - Redis outbox claim / lease
  - GitHub Actions + 서버 배포 스크립트
  - command DLQ 재처리 운영 모드
  - 배포 후 스모크 체크 스크립트

## 24. 현재 기준 남은 핵심 작업

- 실제 Discord 운영 환경에서 `gateway` / `audio-node` 이중 JDA 세션 검증
- command DLQ 재처리 빈도와 실패 패턴 관찰 후 운영 절차 보정
- observability 스택 도입 여부 결정
- 필요하다면 `Gateway -> Worker -> Audio Node` 완전 3프로세스 분리 검토
