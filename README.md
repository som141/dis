# Discord Music Bot

현재 저장소는 멀티 모듈 구조로 정리되어 있으며, 실행 단위는 `gateway-app`과 `audio-node-app` 두 개다. 공용 도메인, 재생 엔진, Redis/RabbitMQ 인프라는 `modules/common-core`가 제공한다.

## 현재 구조

```text
apps/
  gateway-app/
  audio-node-app/
modules/
  common-core/
docs/
ops/
docker-compose.yml
deploy.sh
```

- `apps/gateway-app`
  - Discord slash command 진입점
  - autocomplete 처리
  - `MusicCommand` 생성
  - RabbitMQ RPC producer
- `apps/audio-node-app`
  - RabbitMQ command consumer
  - 실제 재생, 음성 연결, 복구 실행
  - 유휴 음성 채널 자동 퇴장
- `modules/common-core`
  - 공용 command / event 계약
  - playback 코어
  - Redis repository
  - RabbitMQ command infrastructure
  - 공통 Spring bootstrap / observability 설정

## 패키지 루트

현재 패키지 기준은 아래처럼 고정했다.

- `discordgateway.gateway.*`
- `discordgateway.audionode.*`
- `discordgateway.common.*`
- `discordgateway.playback.*`
- `discordgateway.infra.*`

즉, 앱 경계와 패키지 경계가 맞도록 정리된 상태다.

## 고정된 런타임 경로

- 상태 저장소: Redis
- 명령 전달: RabbitMQ RPC
- 이벤트 전달: Spring local event
- in-memory fallback: 제거됨
- in-process command bus: 제거됨

## 빠른 시작

### 1. 환경 변수 준비

```powershell
Copy-Item .env.example .env
```

최소 필요 값:

- `DISCORD_TOKEN`
- `RABBITMQ_USERNAME`
- `RABBITMQ_PASSWORD`

선택 값:

- `DISCORD_DEV_GUILD_ID`
- `YOUTUBE_REFRESH_TOKEN`
- `YOUTUBE_PO_TOKEN`
- `YOUTUBE_VISITOR_DATA`
- `YOUTUBE_REMOTE_CIPHER_*`
- `GRAFANA_*`

### 2. 전체 빌드

```powershell
.\gradlew.bat bootJarAll
```

산출물:

- `apps/gateway-app/build/libs/gateway-app.jar`
- `apps/audio-node-app/build/libs/audio-node-app.jar`

### 3. 로컬 실행

기본 스택:

```powershell
docker compose up -d --build
```

관측성 포함:

```powershell
docker compose --profile observability up -d --build
```

### 4. 개별 실행

Gateway:

```powershell
.\gradlew.bat :apps:gateway-app:bootJar
java -jar apps/gateway-app/build/libs/gateway-app.jar
```

Audio Node:

```powershell
.\gradlew.bat :apps:audio-node-app:bootJar
java -jar apps/audio-node-app/build/libs/audio-node-app.jar
```

## 포트

- Gateway actuator: `8081`
- Audio Node actuator: `8082`
- Redis: `6379`
- RabbitMQ AMQP: `5672`
- RabbitMQ UI: `15672`
- Grafana: `3000`
- Prometheus: `9090`
- Loki: `3100`
- Alloy UI: `12345`

## 관측성

현재 관측성 스택은 아래 기준으로 정리되어 있다.

- Prometheus metrics
- ECS JSON structured logging
- Loki + Alloy 수집
- Grafana datasource / dashboard provisioning
- Prometheus alert rule
- Grafana managed alert rule
- Discord webhook 알림 경로

기본 수신자는 `observability-noop`이다. 실제 Discord 알림을 쓰려면:

- `GRAFANA_ALERT_DEFAULT_RECEIVER=observability-discord`
- `GRAFANA_ALERT_DISCORD_WEBHOOK_URL=<실제 webhook>`

## 원격 배포

GitHub Actions는 `main` push 시 자동 실행된다. 현재 배포는 아래를 함께 올린다.

- `gateway-app` 이미지
- `audio-node-app` 이미지
- `docker-compose.yml`
- `ops/`
- `ops/observability/`
- `.env.cicd`

`OBSERVABILITY_ENABLED=true`면 원격 서버에서도 `prometheus`, `loki`, `alloy`, `redis-exporter`, `grafana`를 함께 올린다.

## 문서

- [문서 인덱스](docs/README.md)
- [현재 아키텍처](docs/CURRENT_ARCHITECTURE.md)
- [코드베이스 분석](docs/CODEBASE_ANALYSIS.md)
- [모듈 구조](docs/MODULE_STRUCTURE.md)
- [이벤트 계약](docs/EVENT_CONTRACT.md)
- [운영 런북](docs/OPERATIONS_RUNBOOK.md)
- [배포 스크립트 가이드](docs/SERVER_DEPLOY_SCRIPT.md)
- [관측성 계획](docs/OBSERVABILITY_PLAN.md)
- [관측성 스택 안내](ops/observability/README.md)
- [작업 로그](docs/CODEX_WORK_LOG.md)
