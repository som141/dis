# Discord Music Bot

Spring Boot 기반 Discord 음악 봇이다. 현재 구조는 단일 코드베이스를 유지하면서 `gateway`, `audio-node`, `all` 역할로 분리 실행할 수 있도록 정리되어 있다.

## 현재 구조

- `gateway`
  - Discord slash command 수신
  - command registration
  - RabbitMQ command bus로 명령 전달
- `audio-node`
  - RabbitMQ command consumer
  - 실제 재생 처리
  - 재기동 시 recovery 수행
  - playback event 발행
- 공통 저장소
  - Redis: 길드 상태, 큐, 플레이어 상태, command dedup, event outbox
  - RabbitMQ: command transport, event transport

상세 구조 요약은 [CODEBASE_ANALYSIS.md](C:/Users/s0302/OneDrive/바탕%20화면/portpolio/dis/CODEBASE_ANALYSIS.md), 현재 아키텍처 다이어그램과 컴포넌트 설명은 [CURRENT_ARCHITECTURE.md](C:/Users/s0302/OneDrive/바탕%20화면/portpolio/dis/CURRENT_ARCHITECTURE.md), 이벤트 계약은 [EVENT_CONTRACT.md](C:/Users/s0302/OneDrive/바탕%20화면/portpolio/dis/EVENT_CONTRACT.md), 운영 절차는 [OPERATIONS_RUNBOOK.md](C:/Users/s0302/OneDrive/바탕%20화면/portpolio/dis/OPERATIONS_RUNBOOK.md), 작업 이력은 [CODEX_WORK_LOG.md](C:/Users/s0302/OneDrive/바탕%20화면/portpolio/dis/CODEX_WORK_LOG.md)에 정리했다.

## 로컬 실행

### 단일 프로세스

```powershell
.\gradlew.bat bootJar
java -jar build/libs/TBot1-all.jar
```

기본값은 `app.role=all`, `messaging.command-transport=in-process`, `messaging.event-transport=spring`이다.

### 역할 분리 실행

```powershell
java -jar build/libs/TBot1-all.jar --spring.profiles.active=gateway
java -jar build/libs/TBot1-all.jar --spring.profiles.active=audio-node
```

## Docker Compose 실행

1. `.\gradlew.bat bootJar`
2. [.env.example](C:/Users/s0302/OneDrive/바탕%20화면/portpolio/dis/.env.example)을 참고해 `.env` 생성
3. `docker compose up --build`

서버 배포에서는 `COMPOSE_PROJECT_NAME=discord-bot`을 사용해 Compose 프로젝트 이름을 고정한다.

현재 compose는 아래 서비스를 함께 띄운다.

- `redis`
- `rabbitmq`
- `gateway`
- `audio-node`

기본 포트는 아래와 같다.

- `8081` -> gateway health
- `8082` -> audio-node health
- `6379` -> redis
- `5672` -> rabbitmq AMQP
- `15672` -> rabbitmq management

## 운영 스크립트

배포 후에는 `current/ops` 아래 스크립트를 사용할 수 있다.

- [ops/replay-command-dlq.sh](C:/Users/s0302/OneDrive/바탕%20화면/portpolio/dis/ops/replay-command-dlq.sh)
  - command DLQ 재처리
- [ops/smoke-check.sh](C:/Users/s0302/OneDrive/바탕%20화면/portpolio/dis/ops/smoke-check.sh)
  - health와 compose 상태 점검

배포 흐름은 [SERVER_DEPLOY_SCRIPT.md](C:/Users/s0302/OneDrive/바탕%20화면/portpolio/dis/SERVER_DEPLOY_SCRIPT.md)를 참고하면 된다.
