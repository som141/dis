# Codex 작업 로그

## 현재 기준 요약

현재 저장소는 아래 상태로 정리돼 있다.

- 멀티모듈 구조
  - `modules/common-core`
  - `apps/gateway-app`
  - `apps/audio-node-app`
- Redis 단일 상태 저장소
- RabbitMQ command transport
- RabbitMQ command result transport
- Spring local event
- Docker Compose 기반 로컬/원격 배포
- GitHub Actions 자동 배포
- Grafana / Prometheus / Loki / Alloy 관측성 스택

## Stage 1~6

- 기존 구조 분석
- queue / player state 외부화
- Redis source of truth 정리
- recovery 경로 추가

## Stage 7~16

- gateway / worker / audio-node 경계 정리
- Spring Boot 기반 실행 구조로 전환
- RabbitMQ command transport 보강
- command dedup / DLQ
- event outbox, confirm, claim/lease를 도입했다가 이후 단순화 과정에서 제거

## Stage 17~24

- Docker Compose 기반 gateway / audio-node / redis / rabbitmq 분리
- GitHub Actions 자동 배포
- `deploy.sh` 추가
- DLQ replay 운영 모드
- smoke check 스크립트
- 레거시 컨테이너 정리 흐름

## Stage 25~28

- 멀티모듈 구조 전환
- `common-core`, `gateway-app`, `audio-node-app` 분리
- README와 문서 재정리
- 미사용 fallback, in-memory 구현, selector 경로 제거

## Stage 29~34

- observability 방향 수립
- `/actuator/prometheus` 노출
- ECS JSON structured logging
- Prometheus / Loki / Alloy / Grafana 스택 구성
- Grafana 대시보드와 alert rule provisioning
- 원격 CI/CD에서 observability 스택도 반영되도록 배포 경로 보강

## Stage 35~38

- `VoiceSessionLifecycleService` 추가
- `ops.voice-idle-disconnect-enabled`, `ops.voice-idle-timeout` 추가
- `VoiceChannelIdleDisconnectService`, `VoiceChannelIdleListener` 추가
- 유휴 퇴장 시 마지막 텍스트 채널에 안내 메시지 전송

## Stage 39

- playback 계층의 공개 채널 재생 결과 메시지 제거
- `PlaybackGateway.loadAndPlay(...)`, `playLocalFile(...)`를 `CompletableFuture<CommandResult>` 기반으로 정리
- `MusicCommandBus`를 RPC가 아닌 publish-only bus로 전환
- `MusicCommandEnvelope`, `CommandDispatchAck`, `MusicCommandResultEvent` 추가
- gateway에 `PendingInteractionRepository`, `InteractionResponseContext`, `RabbitMusicCommandResultListener` 추가
- gateway가 `deferReply(true)` 후 command를 publish하고 result event를 받아 original ephemeral reply를 수정하도록 변경
- audio-node가 command 처리 완료 후 `MusicCommandResultEvent`를 RabbitMQ로 발행하도록 변경
- `CommandDlqReplayService`를 새 envelope 포맷 기준으로 정리
- README, 아키텍처 문서, 이벤트 계약 문서를 현재 async ephemeral 구조 기준으로 갱신

## Stage 40

- `InteractionResponseContext`를 `InteractionHook` 보관 방식에서 `interactionToken` 보관 방식으로 변경
- gateway가 pending interaction을 Redis TTL 저장소에 기록하도록 변경
- `RedisPendingInteractionRepository` 추가
- result event 소비 시 JDA로 `InteractionHook.from(jda, token)`을 재구성해 original ephemeral reply를 수정하도록 변경
- gateway 재기동 후에도 interaction token 유효 시간 안에서는 result event를 이어서 처리할 수 있게 정리

## 현재 남은 작업

- Loki 기반 로그 알림 확장
- 봇 전용 비즈니스 메트릭 추가
- OpenTelemetry + Tempo 도입 검토
- 원격 서버 YouTube 재생 안정화

## 현재 검증 상태

최근 변경 기준으로 확인한 항목:

- `compileJava`
- `compileTestJava`

필요 시 추가 확인할 항목:

- `bootJarAll`
- 로컬 Docker Compose 기동
- Discord 수동 시나리오 검증
