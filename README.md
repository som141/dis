# Discord Music Bot

Spring Boot 기반 Discord 음악 봇이다. 현재는 단일 JAR를 유지하면서도 `gateway`, `audio-node`, `all` 역할로 분리 실행할 수 있고, Redis와 RabbitMQ를 붙여 MSA 준비형 구조로 운용할 수 있다.

## 현재 구조

- `gateway`
  - Discord slash command 수신
  - command registration
  - RabbitMQ command bus로 명령 전달
- `audio-node`
  - RabbitMQ command consumer
  - 실제 재생 처리
  - 재기동 복구
  - playback event 발행
- 공통 저장소
  - Redis: 길드 상태, 큐, 플레이어 상태, command dedup, event outbox
  - RabbitMQ: command transport, event transport

상세 구조는 [CODEBASE_ANALYSIS.md](./CODEBASE_ANALYSIS.md), 이벤트 계약은 [EVENT_CONTRACT.md](./EVENT_CONTRACT.md), 작업 이력은 [CODEX_WORK_LOG.md](./CODEX_WORK_LOG.md)를 참고하면 된다.

## 로컬 실행

### 단일 프로세스

```powershell
.\gradlew.bat bootJar
java -jar build/libs/TBot1-all.jar
```

기본값은 `app.role=all`, `messaging.command-transport=in-process`, `messaging.event-transport=spring`이다.

### 분리 프로세스

프로필 파일이 추가되어 아래처럼 실행할 수 있다.

```powershell
java -jar build/libs/TBot1-all.jar --spring.profiles.active=gateway
java -jar build/libs/TBot1-all.jar --spring.profiles.active=audio-node
```

## Docker Compose 실행

1. `.\gradlew.bat bootJar`
2. `.env.example`를 참고해서 `.env` 생성
3. `docker compose up --build`

현재 compose는 아래 서비스를 띄운다.

- `redis`
- `rabbitmq`
- `gateway`
- `audio-node`

포트는 기본적으로 아래를 사용한다.

- `8081` -> gateway health
- `8082` -> audio-node health
- `6379` -> redis
- `5672` -> rabbitmq AMQP
- `15672` -> rabbitmq management
