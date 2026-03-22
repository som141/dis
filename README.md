# Discord Music Bot

현재 저장소는 `gateway-app`, `audio-node-app`, `common-core`를 분리한 멀티 모듈 구조다. 실제 운영 경로는 아래처럼 고정되어 있다.

- Discord 명령 진입: `apps/gateway-app`
- 실제 재생 및 복구 실행: `apps/audio-node-app`
- 공용 도메인, 재생 엔진, Redis/RabbitMQ 인프라: `modules/common-core`
- 상태 저장소: Redis
- 명령 전달: RabbitMQ RPC
- 이벤트 전달: Spring local event
- 관측성 스택: Prometheus + Loki + Alloy + Grafana

## 디렉터리 구조

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

## 현재 아키텍처 요약

- `gateway-app`
  - Discord slash command 수신
  - autocomplete 처리
  - 입력 검증
  - `MusicCommand` 생성
  - RabbitMQ command producer
- `audio-node-app`
  - RabbitMQ command consumer
  - 음성 채널 연결 및 해제
  - 실제 트랙 로드/재생/정지/스킵
  - Redis 상태 복구
  - 유휴 음성 채널 5분 자동 퇴장
- `common-core`
  - 공용 command/event 모델
  - `MusicWorkerService`
  - `PlayerManager`, `TrackScheduler`
  - Redis repository
  - RabbitMQ command infrastructure
  - 공통 Spring bootstrap

## 현재 고정된 설계 원칙

- 인메모리 상태 저장소는 없다.
- in-process command bus는 없다.
- RabbitMQ event outbox는 제거됐다.
- `gateway-app`은 명령을 만들고 보내는 쪽이다.
- `audio-node-app`은 명령을 받아 실제 재생을 수행하는 쪽이다.
- Redis가 현재 source of truth다.

## 빠른 시작

### 1. 환경변수 준비

```powershell
Copy-Item .env.example .env
```

최소한 아래 값은 채워야 한다.

- `DISCORD_TOKEN`
- `RABBITMQ_USERNAME`
- `RABBITMQ_PASSWORD`
- 필요시 `DISCORD_DEV_GUILD_ID`
- YouTube 재생 안정화가 필요하면 `YOUTUBE_REFRESH_TOKEN`, `YOUTUBE_PO_TOKEN`, `YOUTUBE_VISITOR_DATA`, `YOUTUBE_REMOTE_CIPHER_*`

### 2. JAR 빌드

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

Gateway만 실행:

```powershell
.\gradlew.bat :apps:gateway-app:bootJar
java -jar apps/gateway-app/build/libs/gateway-app.jar
```

Audio Node만 실행:

```powershell
.\gradlew.bat :apps:audio-node-app:bootJar
java -jar apps/audio-node-app/build/libs/audio-node-app.jar
```

## 포트

- Gateway health/actuator: `8081`
- Audio Node health/actuator: `8082`
- Redis: `6379`
- RabbitMQ AMQP: `5672`
- RabbitMQ Management: `15672`
- Grafana: `3000`
- Prometheus: `9090`
- Loki: `3100`
- Alloy UI: `12345`

## 관측성

현재 저장소에는 아래가 이미 반영되어 있다.

- `/actuator/prometheus`
- ECS JSON 콘솔 로그
- `commandId`, `correlationId` 기반 MDC 전파
- Grafana datasource provisioning
- Grafana dashboard provisioning
- Prometheus alert rule
- Grafana-managed alert rule
- Discord webhook 기반 알림 라우팅 준비

기본 알림 수신자는 `observability-noop`이다. 실제 Discord 알림을 켜려면 아래 값을 넣어야 한다.

- `GRAFANA_ALERT_DEFAULT_RECEIVER=observability-discord`
- `GRAFANA_ALERT_DISCORD_WEBHOOK_URL=<실제 Discord webhook>`

## 원격 배포

GitHub Actions는 `main` push 시 자동 실행된다. 현재 배포는 다음을 포함한다.

- `gateway-app` 이미지
- `audio-node-app` 이미지
- `docker-compose.yml`
- `ops/`
- `ops/observability/`
- `.env.cicd`

`OBSERVABILITY_ENABLED=true`면 원격 서버에서도 `prometheus`, `loki`, `alloy`, `redis-exporter`, `grafana`가 같이 올라간다.

## 현재 주의점

- Grafana 관리자 계정은 `GF_SECURITY_ADMIN_USER`, `GF_SECURITY_ADMIN_PASSWORD`를 처음 기동할 때만 적용한다. 기존 `grafana-data` 볼륨이 있으면 나중에 env를 바꿔도 로그인 계정은 자동으로 바뀌지 않는다.
- YouTube 재생은 로컬과 원격 서버에서 결과가 다를 수 있다. 현재 구조상 코드 차이보다 서버 IP/ASN, YouTube anti-bot 응답 차이의 영향을 더 크게 받는다.
- `gateway-app`과 `audio-node-app`은 같은 Discord 토큰으로 각각 JDA 세션을 연다. 이 구조는 현재 역할 분리 기준으로 설계된 것이다.

## 문서

- [문서 인덱스](docs/README.md)
- [현재 아키텍처](docs/CURRENT_ARCHITECTURE.md)
- [코드베이스 분석](docs/CODEBASE_ANALYSIS.md)
- [모듈 구조](docs/MODULE_STRUCTURE.md)
- [이벤트 계약](docs/EVENT_CONTRACT.md)
- [운영 런북](docs/OPERATIONS_RUNBOOK.md)
- [배포 스크립트 가이드](docs/SERVER_DEPLOY_SCRIPT.md)
- [옵저버빌리티 계획](docs/OBSERVABILITY_PLAN.md)
- [옵저버빌리티 스택 안내](ops/observability/README.md)
- [작업 로그](docs/CODEX_WORK_LOG.md)
