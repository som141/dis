# Codex 작업 로그

## 1. 작업 기준

- 사용자 요청: 코드 품질 중심으로 다음 단계 계속 진행
- 테스트 실행은 사용자 담당
- 문서는 한글로 작성
- 작업 로그는 스테이지별로 분리
- 각 단계가 끝난 뒤 남은 작업도 기록

## 2. Stage 1

### 상태

- 기존 코드 기준 완료로 판단

### 확인 내용

- Listener, Application, Infrastructure 책임이 기본적으로 나뉘어 있음

## 3. Stage 2

### 상태

- 기존 코드 기준 완료로 판단

### 확인 내용

- 길드 상태를 저장소 포트로 다루는 구조가 이미 형성되어 있었음

## 4. Stage 3

### 상태

- 기존 코드 기준 완료로 판단

### 확인 내용

- `QueueRepository` 기반 큐 외부화가 이미 들어가 있었음
- 따라서 실제 Codex 작업 시작점은 Stage 4로 판단

## 5. Stage 4

### 추가한 요소

- `PlayerState`
- `PlayerStateRepository`
- `InMemoryPlayerStateRepository`
- `RedisPlayerStateRepository`

### 반영한 내용

- 재생기 상태를 저장소로 분리
- pause / resume / stop / autoplay 관련 상태를 외부 저장소에 보존 가능하게 정리

## 6. Stage 5

### 추가한 요소

- `GuildPlaybackLockManager`
- `InMemoryGuildPlaybackLockManager`
- `RedisGuildPlaybackLockManager`

### 반영한 내용

- 다음 곡 결정 기준을 로컬 메모리 큐가 아니라 `QueueRepository`로 전환
- `skip`, `stop`, `clear`가 저장소 기준으로 동작하도록 변경
- 길드 단위 전이 락을 추가해 재생 전이 경쟁 상태를 줄임

## 7. Stage 6

### 추가한 요소

- `PlaybackRecoveryService`
- `PlaybackRecoveryReadyListener`

### 반영한 내용

- 프로세스 재기동 후 저장된 음성 채널과 재생 상태를 기준으로 복구 수행
- `nowPlaying` 우선 복구 후 실패 시 큐 기준 복구로 이어지도록 정리

## 8. Stage 7

### 추가한 요소

- `MusicCommand`
- `MusicCommandBus`
- `InProcessMusicCommandBus`
- `MusicWorkerService`
- `DiscordReferenceResolver`
- `JdaRuntimeContext`
- `JdaDiscordReferenceResolver`

### 반영한 내용

- Gateway 역할과 Worker 역할을 코드 구조상 분리
- `MusicApplicationService`는 명령 발행만 담당
- 실제 재생 처리 로직은 `MusicWorkerService`로 이동

## 9. Stage 8

### 추가한 요소

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

### 반영한 내용

- Spring Boot 기반 워커 호스트로 전환
- 수동 부트스트랩 코드를 Spring 빈 조립 방식으로 변경
- Actuator health / ready lifecycle을 스프링 방식으로 정리

## 10. Stage 9

### 추가한 요소

- `MusicEvent`
- `MusicEventPublisher`
- `MusicEventFactory`
- `SpringMusicEventPublisher`
- `MusicEventLogListener`
- `EVENT_CONTRACT.md`

### 반영한 내용

- Worker와 Audio Node 사이에서 공통으로 쓰는 이벤트 계약을 코드로 고정
- 재생 상태 변화가 이벤트로 기록되도록 정리
- 이벤트 정의 방식을 별도 문서로 남김

## 11. Stage 10

### 추가한 요소

- `MessagingProperties`
- `RabbitMessagingConfiguration`
- `MusicCommandMessage`
- `MusicCommandMessageFactory`
- `CompositeMusicEventPublisher`
- `RabbitMusicCommandBus`
- `RabbitMusicCommandListener`
- `RabbitMusicEventPublisher`

### 반영한 내용

- 명령 transport를 `in-process` 또는 `rabbitmq`로 전환 가능하게 구성
- RabbitMQ RPC 기반 명령 버스를 추가
- 이벤트 발행도 Spring 단독 또는 Spring + RabbitMQ 복합 발행으로 전환 가능하게 구성

## 12. Stage 11

### 추가한 요소

- `MusicProtocol`
- `MusicCommandTrace`
- `MusicCommandTraceContext`

### 반영한 내용

- 명령 envelope와 이벤트 계약에 `schemaVersion`을 추가
- 이벤트에 `correlationId`를 추가
- 비동기 콜백까지 같은 명령 추적 ID가 전파되도록 정리

## 13. Stage 12

### 추가한 요소

- `AppRole`
- `ConditionalOnAppRole`
- `AppRoleCondition`
- `DiscordCommandRegistrationListener`

### 크게 수정한 요소

- `ApplicationFactory`
- `RabbitMessagingConfiguration`
- `DiscordBotListener`
- `application.yml`

### 반영한 내용

- `app.role=all|gateway|audio-node` 역할 분리를 도입
- Slash command 처리와 명령 등록은 gateway 쪽 책임으로 분리
- 복구와 RabbitMQ 명령 소비는 audio-node 쪽 책임으로 분리
- 같은 JAR를 역할별 프로세스로 띄울 수 있는 구조를 만듦

## 14. Stage 13

### 추가한 요소

- `ProcessedCommand`
- `CommandProcessingStatus`
- `ProcessedCommandRepository`
- `InMemoryProcessedCommandRepository`
- `RedisProcessedCommandRepository`

### 반영한 내용

- RabbitMQ command consumer에 멱등 처리 저장소를 추가
- `commandId` 기준으로 중복 명령 재처리를 차단
- command DLQ 구성을 추가
- 이미 완료된 중복 명령은 기존 `CommandResult`를 재사용하도록 정리

## 15. Stage 14

### 추가한 요소

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

### 반영한 내용

- RabbitMQ 이벤트 발행 경로를 `즉시 발행 -> 실패 시 outbox 저장 -> 스케줄러 재전송` 구조로 보강
- outbox 저장소를 메모리/Redis 중 선택 가능하게 연결
- `app.eventOutboxStore`를 추가해 outbox 저장소를 별도 선택 가능하게 함
- `messaging.event-outbox-flush-interval-ms`
- `messaging.event-outbox-retry-delay-ms`
- `messaging.event-outbox-batch-size`

## 16. Stage 15

### 크게 수정한 요소

- `RabbitMusicEventSender`
- `MessagingProperties`
- `application.yml`

### 반영한 내용

- RabbitMQ 이벤트 발행 시 `CorrelationData` 기반 publisher confirm을 기다리도록 변경
- broker가 nack를 반환하거나 confirm timeout이 나면 즉시 실패로 간주
- mandatory return이 발생하면 returned metadata를 포함한 실패로 간주
- `spring.rabbitmq.publisher-confirm-type=correlated`
- `spring.rabbitmq.publisher-returns=true`
- `spring.rabbitmq.template.mandatory=true`
  - 위 설정을 기본값으로 반영
- `messaging.event-publish-confirm-timeout-ms`를 추가해 confirm 대기 시간을 조절 가능하게 함

## 17. Stage 16

### 크게 수정한 요소

- `MusicEventOutboxRepository`
- `PendingMusicEvent`
- `InMemoryMusicEventOutboxRepository`
- `RedisMusicEventOutboxRepository`
- `MusicEventOutboxRelay`
- `MessagingProperties`
- `application.yml`
- `RabbitMessagingConfiguration`

### 반영한 내용

- outbox 저장소 계약을 `poll` 방식에서 `claim/lease` 방식으로 변경
- `PendingMusicEvent`에 `claimOwner`, `claimToken`, `claimUntilEpochMs`를 추가
- relay가 due 이벤트를 그냥 읽는 대신 `claimDue(...)`로 먼저 소유권을 획득하도록 변경
- 성공 시 `claimToken`이 일치할 때만 삭제하고, 실패 시 `claimToken`이 일치할 때만 재예약하도록 변경
- Redis 저장소는 Lua 스크립트로 claim, 성공 삭제, 실패 재예약을 원자 처리하도록 정리
- `messaging.event-outbox-claim-ttl-ms`를 추가해 lease 만료 시간을 제어 가능하게 함

## 18. Stage 17

### 추가한 요소

- `.env.example`
- `application-gateway.yml`
- `application-audio-node.yml`

### 크게 수정한 요소

- `docker-compose.yml`
- `README.md`

### 반영한 내용

- Docker Compose 기준 `gateway`, `audio-node`, `redis`, `rabbitmq`를 함께 띄우는 배포 구성을 추가
- `gateway`와 `audio-node` 각각의 Spring profile을 분리
- profile별로 `app.role`, Redis 저장소 사용, RabbitMQ transport 사용이 자동 적용되도록 정리
- `.env.example`을 추가해 필수 환경변수 입력 기준을 명시
- README를 현재 구조 기준으로 다시 정리

## 19. Stage 18

### 추가한 요소

- `deploy.sh`
- `SERVER_DEPLOY_SCRIPT.md`

### 크게 수정한 요소

- `.github/workflows/cicd-deploy.yml`
- `docker-compose.yml`

### 반영한 내용

- GitHub Actions 배포 워크플로를 현재 구조에 맞게 수정
- `shadowJar` 기반 빌드 대신 `bootJar` 기준 빌드로 변경
- Docker 이미지를 `discord-bot:<git-sha>` 태그로 만들고 tar.gz로 서버에 전달하도록 정리
- `.env.cicd`에 현재 compose가 기대하는 환경변수와 이미지 태그를 기록하도록 변경
- workflow가 서버에 아래 파일을 업로드하도록 변경
  - 이미지 tar.gz
  - `.env.cicd`
  - `docker-compose.yml`
  - `deploy.sh`
- 서버 `deploy.sh`가 아래 순서로 배포하도록 정리
  - 릴리스 디렉터리 생성
  - compose / env / 이미지 복사
  - `docker load`
  - `docker compose up -d --no-build --remove-orphans`
  - 오래된 릴리스 정리
- compose 이미지 이름을 `${APP_IMAGE_NAME}:${APP_IMAGE_TAG}` 형식으로 변경해 GitHub Actions가 만든 태그와 맞춤
- 서버 쉘 스크립트 구조와 운영 방법을 별도 문서로 정리

## 20. 검증

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
- GitHub Actions 실제 배포 실행
  - 로컬에서 실행하지 않음

## 21. 현재 기준 남은 작업

- command DLQ 재처리 운영 절차와 관리자용 재처리 도구를 마련
- gateway / audio-node 이중 JDA 세션 운영 시 실제 Discord 연결 정책과 복구 동작을 검증
- 필요하다면 `Gateway -> Worker -> Audio Node` 3프로세스 구조로 Worker를 더 분리할지 검토

## 22. 원본 지침 md 대비 진행률

기준 문서:

- `discord_msa_codex_instructions.md`

진행률 판단:

- 원본 문서 전체 8개 스테이지 기준
  - Stage 1 완료
  - Stage 2 완료
  - Stage 3 완료
  - Stage 4 완료
  - Stage 5 완료
  - Stage 6 완료
  - Stage 7 대부분 완료
  - Stage 8 미완료
- 즉, 원본 문서 기준으로는 대략 `7 / 8 단계`, 비율로는 약 `87.5%` 수준까지 왔다고 보는 게 맞다.

원본 문서와 달라진 점:

- 원본 문서는 Stage 3부터 시작한다고 적혀 있었지만 실제 코드에는 Stage 3의 일부가 이미 선반영되어 있었다.
- 원본 문서는 `Gateway -> Worker -> Audio Node` 3프로세스 분리를 직접 목표로 잡았지만, 현재 구현은 `Spring Boot 단일 코드베이스 + gateway 역할 + audio-node 역할 + 내부 worker 코어` 구조다.
- 원본 문서는 RabbitMQ를 나중 단계로 언급했지만, 현재 코드는 command bus, event publisher, DLQ, outbox, publisher confirm, claim/lease까지 이미 반영했다.
- 원본 문서는 observability를 Stage 8로 잡았지만, 현재는 observability보다 분산 안정성과 메시징 신뢰성 쪽을 먼저 깊게 구현했다.
- 원본 문서에 없던 추가 구현이 많다.
  - Spring Boot 워커 전환
  - event contract 문서화
  - `schemaVersion`, `correlationId`
  - command dedup
  - event outbox
  - publisher confirm / mandatory return
  - Redis outbox claim / lease
  - 역할별 profile / compose 배포 구성
  - GitHub Actions + 서버 deploy.sh 배포 구성

원본 문서 기준으로 아직 남은 핵심:

- Stage 8 observability
  - Prometheus
  - Grafana
  - Loki / Promtail
- 운영 절차 문서화
- 실제 분리 배포 환경 검증
