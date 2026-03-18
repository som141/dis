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

## 21. Stage 20

### 추가 요소

- `CURRENT_ARCHITECTURE.md`

### 반영 내용

- 현재 배포 토폴로지를 다이어그램으로 정리
- 명령 흐름, 재생 흐름, 복구 흐름, 이벤트 흐름을 분리해 문서화
- gateway, audio-node, command bus, worker core, playback engine, Redis, RabbitMQ, 운영 모드, 배포 파이프라인별 특징과 워크로드를 표로 정리
- 저지연 요청형, 장시간 상태 유지형, 공유 상태 관리형, 운영 보조형 워크로드로 분류해 설명

## 22. Stage 21

### 크게 수정한 요소

- `docker-compose.yml`
- `deploy.sh`
- `.github/workflows/cicd-deploy.yml`
- `.env.example`
- `ops/replay-command-dlq.sh`
- `ops/smoke-check.sh`
- `SERVER_DEPLOY_SCRIPT.md`
- `OPERATIONS_RUNBOOK.md`
- `README.md`

### 반영 내용

- 서버 배포 시 Compose 프로젝트 이름을 `discord-bot`으로 고정
- 릴리스 디렉터리 이름이 바뀌어도 같은 네트워크와 볼륨을 재사용하도록 정리
- 이전 릴리스가 있으면 새 릴리스 기동 전에 먼저 내리도록 deploy 스크립트 보강
- `container_name` 고정을 제거해 Compose 기본 수명주기 관리와 충돌하지 않도록 수정
- 운영 스크립트도 같은 Compose 프로젝트 이름을 사용하도록 정리
- 예전 단일 컨테이너 `dis-bot`과 `container_name` 기반 레거시 컨테이너가 남아 있어도 다음 배포에서 자동 정리되도록 deploy 스크립트 보강

## 23. Stage 22

### 추가 요소

- `ops/cleanup-legacy-deploy.sh`

### 크게 수정한 요소

- `.github/workflows/cicd-deploy.yml`
- `README.md`
- `SERVER_DEPLOY_SCRIPT.md`
- `OPERATIONS_RUNBOOK.md`

### 반영 내용

- 예전 단일 배포와 구형 컨테이너를 서버에서 한 번에 정리할 수 있는 cleanup 스크립트 추가
- 필요 시 `discord-bot:*` 이미지까지 제거할 수 있는 선택 경로 추가
- 운영 스크립트 업로드 대상에 cleanup 스크립트를 포함

## 24. Stage 23

### 크게 수정한 요소

- `deploy.sh`
- `SERVER_DEPLOY_SCRIPT.md`

### 반영 내용

- 서버 배포 스크립트에 단계별 로그 출력 추가
- 실패 시 몇 번째 줄에서 종료됐는지 바로 보이도록 에러 트랩 추가
- 이전 릴리스 `docker compose down`이 실패해도 레거시 컨테이너 정리와 새 릴리스 기동으로 이어지도록 보강
- 구형 릴리스 `.env`에 `COMPOSE_PROJECT_NAME`이 없어도 이전 릴리스 정리 단계가 중단되지 않도록 호환 처리

## 25. Stage 24

### 크게 수정한 요소

- `.github/workflows/cicd-deploy.yml`
- `SERVER_DEPLOY_SCRIPT.md`

### 반영 내용

- GitHub Actions가 `DISCORD_TOKEN` 시크릿이 없더라도 레거시 `TOKEN` 시크릿을 fallback으로 사용할 수 있게 수정
- 서버 배포 문서에도 `DISCORD_TOKEN` 또는 `TOKEN` 둘 다 허용된다고 명시

## 26. 검증

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

## 27. 원본 지시 md 대비 진행률

기준 문서:

- `discord_msa_codex_instructions.md`

현재 판단:

- 원본 문서 기준 큰 단계는 사실상 `7 / 8` 수준까지 진행
- 비율로 보면 약 `87.5%`

## 28. 원본 문서와 달라진 점

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

## 29. 현재 기준 남은 핵심 작업

- 실제 Discord 운영 환경에서 `gateway` / `audio-node` 이중 JDA 세션 검증
- command DLQ 재처리 빈도와 실패 패턴 관찰 후 운영 절차 보정
- observability 스택 도입 여부 결정
- 필요하다면 `Gateway -> Worker -> Audio Node` 완전 3프로세스 분리 검토

## 31. Stage 26

### 크게 수정한 요소

- `README.md`
- `apps/gateway-app/README.md`
- `apps/audio-node-app/README.md`
- `modules/common-core/README.md`
- `docs/README.md`
- `docs/`

### 반영 내용

- 루트 README를 저장소 인덱스 성격으로 재작성
- gateway-app, audio-node-app, common-core 각각의 전용 README 추가
- 상위 분석/운영/배포/기록 문서를 `docs/` 디렉터리로 이동
- `docs/README.md`를 추가해서 문서 탐색 진입점을 분리
- 모듈 구조 설명 문서를 `docs/MODULE_STRUCTURE.md` 기준으로 재정리

## 30. Stage 25

### 크게 수정한 요소

- `build.gradle`
- `settings.gradle`
- `docker-compose.yml`
- `deploy.sh`
- `.github/workflows/cicd-deploy.yml`
- `.env.example`
- `README.md`
- `MODULE_STRUCTURE.md`
- `apps/gateway-app/**`
- `apps/audio-node-app/**`
- `modules/common-core/**`

### 반영 내용

- 단일 모듈 구조를 `modules/common-core`, `apps/gateway-app`, `apps/audio-node-app` 멀티모듈 구조로 전환
- 공용 도메인, 오디오 엔진, Redis/RabbitMQ 인프라, 공용 Spring 설정을 `common-core`로 이동
- Discord 명령 진입점과 gateway 전용 서비스를 `gateway-app`으로 이동
- recovery와 audio-node 전용 서비스를 `audio-node-app`으로 이동
- 각 앱이 자체 `main class`, 자체 `application.yml`, 자체 `bootJar`를 가지도록 분리
- 단일 `TBot1-all.jar` 대신 `gateway-app.jar`, `audio-node-app.jar` 생성 구조로 변경
- 루트 Dockerfile 제거, 앱별 Dockerfile 도입
- compose를 `gateway 이미지`와 `audio-node 이미지` 각각 빌드/실행하는 구조로 변경
- 배포 스크립트와 GitHub Actions도 두 이미지 기준으로 적재/배포하도록 수정

### 검증

- `.\gradlew.bat :modules:common-core:compileJava :apps:gateway-app:compileJava :apps:audio-node-app:compileJava :modules:common-core:compileTestJava`
  - 성공
- `.\gradlew.bat bootJarAll`
  - 성공
- `docker compose up -d --build gateway audio-node`
  - 성공
- `docker compose ps`
  - `gateway`, `audio-node`, `redis`, `rabbitmq` 모두 healthy 확인

## 32. Stage 27

### 크게 수정한 요소

- `modules/common-core/src/main/java/discordgateway/bootstrap/ApplicationFactory.java`
- `modules/common-core/src/main/java/discordgateway/bootstrap/AppProperties.java`
- `modules/common-core/src/main/java/discordgateway/bootstrap/MessagingProperties.java`
- `modules/common-core/src/main/java/discordgateway/bootstrap/RabbitMessagingConfiguration.java`
- `modules/common-core/src/main/java/discordgateway/bootstrap/RedisStorageHealthIndicator.java`
- `apps/gateway-app/src/main/java/discordgateway/gateway/GatewayComponentConfiguration.java`
- `apps/audio-node-app/src/main/java/discordgateway/audionode/AudioNodeComponentConfiguration.java`
- `apps/gateway-app/src/main/resources/application.yml`
- `apps/audio-node-app/src/main/resources/application.yml`
- `modules/common-core/src/main/resources/application-common.yml`

### 삭제한 요소

- `InMemory*Repository`
- `InMemoryGuildPlaybackLockManager`
- `InProcessMusicCommandBus`
- `CompositeMusicEventPublisher`
- `AppRole`, `ConditionalOnAppRole`, `AppRoleCondition`
- `MusicEventOutboxRepository`
- `PendingMusicEvent`
- `RabbitMusicEventPublisher`
- `RabbitMusicEventSender`
- `MusicEventOutboxRelay`
- `RedisMusicEventOutboxRepository`
- 관련 memory 테스트 2건

### 반영 내용

- 공용 저장소 경로를 Redis 구현으로 고정
- command 전송 경로를 RabbitMQ로 고정
- 이벤트 발행 경로를 Spring local event로 고정
- 저장소/transport 선택 분기를 없애기 위해 `app.role`, `state-store`, `queue-store`, `player-state-store`, `command-dedup-store`, `event-outbox-store`, `messaging.command-transport`, `messaging.event-transport` 설정 제거
- gateway는 `RabbitMusicCommandBus`만 생성하도록 앱 모듈로 이동
- audio-node는 `RabbitMusicCommandListener`만 생성하도록 앱 모듈로 이동
- health는 Redis 기준으로만 판정하도록 단순화
- 현재 구조 문서와 앱별 README를 실제 코드 기준으로 다시 작성

### 검증

- `.\gradlew.bat :modules:common-core:compileJava :apps:gateway-app:compileJava :apps:audio-node-app:compileJava :modules:common-core:compileTestJava`
  - 성공

## 33. Stage 28

### 크게 정리한 요소

- 루트 `README.md`
- `docs/README.md`
- `modules/common-core/README.md`
- 루트 로컬 산출물과 IDE 메타파일

### 삭제한 요소

- 루트 `img.png`
- `modules/common-core/src/main/resources/META-INF/persistence.xml`
- `docs/discord_msa_codex_instructions.md`
- 루트 `.idea/`
- 루트 `.gradle/`
- 루트 `build/`
- 루트 `out/`
- 각 모듈의 생성된 `build/` 디렉터리

### 반영 내용

- 저장소 루트를 실행 코드, 배포 파일, 문서만 남는 구조로 재정리
- 더 이상 현재 구조 설명에 필요 없는 과거 입력 문서 제거
- 공용 코어 README에서 삭제된 리소스 항목 반영
- 루트 README를 현재 최소 구조 기준으로 다시 작성
- 문서 인덱스를 현재 유지 중인 문서만 보이도록 다시 작성

### 검증

- `.\gradlew.bat :modules:common-core:compileJava :apps:gateway-app:compileJava :apps:audio-node-app:compileJava :modules:common-core:compileTestJava`
  - 성공
- 검증 후 생성된 `build/`, `.gradle/` 산출물 재정리 완료

## 34. Stage 29

### 크게 수정한 요소

- `docs/OBSERVABILITY_PLAN.md`
- `docs/README.md`
- `README.md`

### 반영 내용

- 현재 구조에 맞는 로그 수집 / 메트릭 / 트레이싱 / 알람 권장안을 별도 문서로 정리
- `Grafana + Prometheus + Loki + Alloy`를 1차 권장안으로 제시
- `OpenTelemetry Java agent + Tempo`를 2차 권장안으로 제시
- Promtail은 2026-03-02 EOL 기준으로 신규 도입 비권장 사항으로 정리
- 문서 인덱스와 루트 README에 관측성 계획 문서 링크 추가

### 참고 출처

- Grafana Alloy 공식 문서
- Grafana Loki / Promtail 공식 문서
- Prometheus 공식 문서
- Spring Boot metrics 공식 문서
- OpenTelemetry Java 공식 문서
- Grafana Alerting 공식 문서

## 35. Stage 30

### 수정한 요소

- `modules/common-core/build.gradle`
- `modules/common-core/src/main/resources/application-common.yml`
- `modules/common-core/src/main/resources/logback-spring.xml`
- `modules/common-core/src/main/java/discordgateway/application/MusicCommandTraceContext.java`
- `modules/common-core/src/main/java/discordgateway/application/MusicWorkerService.java`
- `modules/common-core/src/main/java/discordgateway/application/event/MusicEventLogListener.java`
- `modules/common-core/src/main/java/discordgateway/bootstrap/ApplicationFactory.java`
- `modules/common-core/src/main/java/discordgateway/bootstrap/CommandDlqReplayRunner.java`
- `modules/common-core/src/main/java/discordgateway/infrastructure/messaging/rabbit/RabbitMusicCommandListener.java`
- `modules/common-core/README.md`
- `docs/OBSERVABILITY_PLAN.md`

### 반영 내용

- `micrometer-registry-prometheus`를 추가해서 Spring Boot Actuator가 `/actuator/prometheus`를 노출할 수 있게 정리
- 공통 설정에서 Actuator 노출 범위를 `health,info,prometheus`로 확장
- `management.metrics.tags.*`로 기본 메트릭에 `application`, `node` 태그를 붙이도록 정리
- 기존 평문 `logback.xml`을 `logback-spring.xml` 기반 구조 로그 설정으로 교체
- 콘솔 로그를 Spring Boot structured logging `ecs` 포맷으로 고정
- `MusicCommandTraceContext`에 MDC 전파를 추가해서 `commandId`, `correlationId`, `producer`, `schemaVersion`가 구조 필드로 남도록 정리
- 핵심 운영 로그를 `addKeyValue(...)` 기반 구조 로그로 전환
  - startup-config
  - music-command rpc
  - music-event
  - join / play 요청 로그
  - command DLQ replay 결과
- 관측성 계획 문서도 실제 반영된 파일 경로 기준으로 갱신

### 검증

- `.\gradlew.bat :modules:common-core:compileJava :apps:gateway-app:compileJava :apps:audio-node-app:compileJava :modules:common-core:compileTestJava`
  - 성공

## 36. Stage 31

### 수정한 요소

- `docker-compose.yml`
- `.env.example`
- `ops/observability/README.md`
- `ops/observability/prometheus/prometheus.yml`
- `ops/observability/loki/loki-config.yml`
- `ops/observability/alloy/config.alloy`
- `ops/observability/grafana/provisioning/datasources/datasources.yml`
- `ops/observability/grafana/provisioning/dashboards/dashboards.yml`
- `ops/observability/grafana/dashboards/README.md`
- `README.md`
- `docs/README.md`
- `docs/OPERATIONS_RUNBOOK.md`

### 반영 내용

- 관측성 스택을 기본 앱 실행과 분리하기 위해 `docker compose profile` 기반 `observability` 구성을 추가
- `prometheus`, `loki`, `alloy`, `redis-exporter`, `grafana` 서비스를 compose에 추가
- RabbitMQ에 `rabbitmq_prometheus` 플러그인을 켜서 Prometheus scrape 경로를 제공하도록 정리
- Prometheus scrape 대상을 애플리케이션, Redis exporter, RabbitMQ, Prometheus, Loki, Alloy로 정리
- Alloy는 Docker socket 기반으로 컨테이너 stdout 로그를 Loki로 전달하도록 구성
- Grafana datasource provisioning을 추가해서 Prometheus / Loki를 자동 등록
- `.env.example`에 `APP_ENV`, Grafana 관리자 계정 관련 변수 추가
- README와 운영 런북에 관측성 스택 실행 명령과 접속 경로 반영

### 검증

- `docker compose config`
  - 성공
- `docker compose --profile observability config`
  - 성공
- `docker compose --profile observability up -d prometheus loki alloy redis-exporter grafana`
  - 성공
- `http://127.0.0.1:9090/-/ready`
  - `200`
- `http://127.0.0.1:3100/ready`
  - `200`
- `http://127.0.0.1:3000/api/health`
  - `200`
- `http://127.0.0.1:8081/actuator/prometheus`
  - `200`
- `http://127.0.0.1:8082/actuator/prometheus`
  - `200`

## 37. Stage 32

### 수정한 파일

- `docker-compose.yml`
- `ops/observability/prometheus/prometheus.yml`
- `ops/observability/prometheus/alerts.yml`
- `ops/observability/grafana/dashboards/discord-bot-app-overview.json`
- `ops/observability/grafana/dashboards/discord-bot-infra-overview.json`
- `ops/observability/README.md`
- `ops/observability/grafana/dashboards/README.md`
- `docs/OBSERVABILITY_PLAN.md`
- `docs/OPERATIONS_RUNBOOK.md`

### 반영 내용

- Prometheus 설정에 `rule_files`를 추가하고 기본 알림 규칙 파일을 연결
- Grafana file provisioning 경로에 기본 대시보드 2개 추가
  - 앱 상태 / JVM / CPU / 스레드 / 로그율
  - Redis / RabbitMQ / 관측 스택 상태
- 현재 실제로 노출되는 메트릭 이름 기준으로 알림 식을 고정
- 운영 문서에 대시보드 이름과 기본 알림 규칙을 반영

### 검증 예정 항목

- `docker compose --profile observability up -d prometheus grafana`
- `http://127.0.0.1:9090/api/v1/rules`
- Grafana dashboard search API 확인

## 38. Stage 33

### 수정한 파일

- `docker-compose.yml`
- `.env.example`
- `ops/observability/grafana/provisioning/alerting/contact-points.yml`
- `ops/observability/grafana/provisioning/alerting/notification-policies.yml`
- `ops/observability/grafana/provisioning/alerting/alert-rules.yml`
- `ops/observability/README.md`
- `docs/OBSERVABILITY_PLAN.md`
- `docs/OPERATIONS_RUNBOOK.md`

### 반영 내용

- Grafana alerting file provisioning 디렉터리 추가
- 기본 contact point 를 `observability-noop` 로 두고, Discord webhook 을 위한 `observability-discord` contact point 추가
- Grafana-managed alert rule 4개 추가
  - `GatewayDownGrafana`
  - `AudioNodeDownGrafana`
  - `AppHighJvmHeapUsageGrafana`
  - `RabbitMqConsumerMissingGrafana`
- 운영 문서에 Discord webhook 기반 활성화 절차 반영

### 검증 예정 항목

### 검증

- `docker compose --profile observability up -d grafana`
  - 성공
- `http://127.0.0.1:3000/api/health`
  - `200`
- `http://127.0.0.1:3000/api/v1/provisioning/contact-points`
  - `observability-noop`, `observability-discord` 확인
- `http://127.0.0.1:3000/api/v1/provisioning/policies`
  - 기본 receiver `observability-noop` 확인
- `http://127.0.0.1:3000/api/v1/provisioning/alert-rules`
  - Grafana-managed rule 4개 확인
- `http://127.0.0.1:3000/api/ruler/grafana/api/v1/rules`
  - `discord-bot-grafana-managed` rule group 확인
