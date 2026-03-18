# Codex 작업 로그

## 현재 기준 요약

현재 저장소는 아래 상태까지 정리되어 있다.

- 멀티모듈 구조
  - `modules/common-core`
  - `apps/gateway-app`
  - `apps/audio-node-app`
- Redis 단일 상태 저장소
- RabbitMQ command RPC
- Spring local event
- command dedup + DLQ 재처리
- recovery 경로
- Docker Compose 기반 로컬/원격 배포
- GitHub Actions 자동 배포
- observability stack
  - Prometheus
  - Loki
  - Alloy
  - Grafana
- 대시보드 / alert provisioning
- 원격 CI/CD에서 관측성 스택 자동 반영 옵션

## Phase 1. 구조 파악과 상태 외부화

### Stage 1~3

- 기존 코드 구조 분석
- 기존 저장소와 책임 분리 상태 확인
- Stage 3 범위는 이미 반영되어 있음을 확인

### Stage 4

- player 상태를 저장소로 분리
- `PlayerState`, `PlayerStateRepository` 정리

### Stage 5

- 큐를 저장소 기준으로 전이하도록 정리
- guild 단위 재생 락 추가

### Stage 6

- `PlaybackRecoveryService`
- `PlaybackRecoveryReadyListener`
- Redis 기반 복구 경로 추가

## Phase 2. Worker 분리와 Spring 정리

### Stage 7

- `MusicApplicationService`와 `MusicWorkerService` 역할 분리
- command bus 경계 도입

### Stage 8

- Spring Boot 기반 bootstrap 전환
- 설정 클래스로 공통 설정 정리

### Stage 9

- `MusicEvent` 계약 도입
- 이벤트 문서화 시작

### Stage 10~16

- RabbitMQ command 경로 강화
- command envelope, trace, correlation 정리
- command dedup / DLQ 추가
- event outbox, confirm, claim/lease 등을 도입했다가 이후 구조 단순화 과정에서 제거

## Phase 3. 배포와 운영 보강

### Stage 17~24

- Docker Compose 기반 gateway/audio-node/redis/rabbitmq 분리
- GitHub Actions 기반 자동 배포
- `deploy.sh` 도입
- DLQ replay 운영 모드
- smoke check 스크립트
- legacy 컨테이너 정리 스크립트
- compose project name 고정
- `DISCORD_TOKEN` / `TOKEN` fallback 정리

## Phase 4. 멀티모듈 전환과 구조 단순화

### Stage 25

- 단일 앱 구조를 멀티모듈 구조로 전환
- `common-core`, `gateway-app`, `audio-node-app` 분리
- 개별 bootJar / 개별 Dockerfile 정리

### Stage 26

- 루트 README, 모듈 README, `docs/` 인덱스 정리

### Stage 27

- 인메모리 저장소 제거
- in-process bus 제거
- role selector 제거
- event outbox / Rabbit event transport 제거
- 현재 운영 경로만 남기는 단순화 완료

### Stage 28

- 불필요 파일, 예전 문서, 잔여 산출물 정리

## Phase 5. 관측성

### Stage 29

- 관측성 방향 정리
- Grafana + Prometheus + Loki + Alloy 채택

### Stage 30

- `/actuator/prometheus` 반영
- ECS JSON structured logging 반영
- MDC 기반 `commandId`, `correlationId` 전파

### Stage 31

- `docker-compose`에 observability profile 추가
- `prometheus`, `loki`, `alloy`, `redis-exporter`, `grafana` 추가
- datasource provisioning 추가

### Stage 32

- Prometheus alert rules 추가
- 기본 대시보드 2종 추가

### Stage 33

- Grafana-managed alert rules 추가
- contact point / notification policy provisioning 추가

### Stage 34

- GitHub Actions와 `deploy.sh`가 `ops/observability/**`를 원격으로 반영하도록 수정
- `OBSERVABILITY_ENABLED` 기준 자동 기동 추가

## 현재 남은 작업

- 실제 운영 Discord webhook 연결
- Loki 기반 로그 알림 확장
- 봇 전용 비즈니스 메트릭 추가
- OpenTelemetry + Tempo 도입 검토
- 원격 서버 YouTube 재생 안정화

## 현재 검증 상태

최근 구조 정리와 관측성 작업 기준으로 반복 확인한 항목:

- `compileJava`
- `compileTestJava`
- `bootJarAll`
- `docker compose config`
- `docker compose --profile observability config`
- 로컬 observability stack 기동

운영 환경에서 별도 확인이 필요한 항목:

- Discord webhook 실제 발송
- 원격 서버 Grafana 계정 최종 상태
- 원격 서버 YouTube 재생 성공률
