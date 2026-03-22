# Discord Music Bot

현재 저장소는 `gateway-app + audio-node-app + common-core` 멀티모듈 구조다.

- `apps/gateway-app`
  - Discord slash command 진입점
  - deferred ephemeral 응답 시작
  - RabbitMQ command publish
  - command result event 소비 후 original ephemeral reply 수정
- `apps/audio-node-app`
  - RabbitMQ command consumer
  - 실제 음성 연결, 곡 로드, 재생, 복구, 유휴 퇴장 수행
  - command 처리 결과를 RabbitMQ result event로 발행
- `modules/common-core`
  - 공용 command / event 계약
  - playback 코어
  - Redis / RabbitMQ / JDA 인프라
  - 공용 Spring bootstrap / observability 설정

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

## 고정된 실행 경로

- 상태 저장소: Redis
- command transport: RabbitMQ async publish
- command result transport: RabbitMQ direct exchange
- 내부 상태 이벤트: Spring local event
- 사용자 응답 기본값: Discord ephemeral
- in-memory fallback: 제거
- in-process command bus: 제거

## 빠른 시작

### 1. 환경변수 준비

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

## 현재 응답 흐름

1. 사용자가 slash command 실행
2. gateway가 `deferReply(true)` 수행
3. gateway가 `MusicCommandEnvelope`를 RabbitMQ로 publish
4. audio-node가 command를 consume
5. audio-node가 실제 재생 로직 실행
6. audio-node가 `MusicCommandResultEvent` 발행
7. gateway가 result event를 받아 original ephemeral reply를 수정

즉 현재는 RPC 응답을 기다리지 않고, 비동기 결과 이벤트로 Discord 응답을 마무리한다.

## 관측성

현재 관측성 스택은 아래 기준으로 정리돼 있다.

- Prometheus metrics
- ECS JSON structured logging
- Loki + Alloy 수집
- Grafana datasource / dashboard provisioning
- Prometheus alert rule
- Grafana managed alert rule
- Discord webhook 알림 경로

기본 수신자는 `observability-noop`이다. 실제 Discord 알림을 켜려면:

- `GRAFANA_ALERT_DEFAULT_RECEIVER=observability-discord`
- `GRAFANA_ALERT_DISCORD_WEBHOOK_URL=<실제 webhook>`

## 원격 배포

GitHub Actions는 `main` push 시 자동 실행된다. 현재 배포는 아래를 포함한다.

- `gateway-app` 이미지
- `audio-node-app` 이미지
- `docker-compose.yml`
- `ops/`
- `ops/observability/`
- `.env.cicd`

`OBSERVABILITY_ENABLED=true`면 원격 서버에서 `prometheus`, `loki`, `alloy`, `redis-exporter`, `grafana`도 같이 기동한다.

## 문서

- [문서 인덱스](docs/README.md)
- [현재 아키텍처](docs/CURRENT_ARCHITECTURE.md)
- [코드베이스 분석](docs/CODEBASE_ANALYSIS.md)
- [모듈 구조](docs/MODULE_STRUCTURE.md)
- [이벤트 계약](docs/EVENT_CONTRACT.md)
- [운영 러너북](docs/OPERATIONS_RUNBOOK.md)
- [배포 스크립트 가이드](docs/SERVER_DEPLOY_SCRIPT.md)
- [관측성 계획](docs/OBSERVABILITY_PLAN.md)
- [관측성 스택 안내](ops/observability/README.md)
- [작업 로그](docs/CODEX_WORK_LOG.md)
