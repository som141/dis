# 운영 런북

## 1. 목적

이 문서는 현재 배포 구조에서 운영자가 바로 써야 하는 절차를 정리한다.

- Linux 서버 런타임 주의점
- command DLQ 재처리 방법
- 배포 직후 스모크 체크 방법
- `gateway` / `audio-node` 이중 JDA 세션 검증 포인트

## 2. Linux 런타임 정리

현재 애플리케이션 이미지는 [Dockerfile](C:/Users/s0302/OneDrive/바탕%20화면/portpolio/dis/Dockerfile) 기준 `eclipse-temurin:21-jre-alpine`를 사용한다.

따라서 음성 관련 네이티브 라이브러리는 Alpine Linux 기준 `musl` 호환본이 필요하다.

[build.gradle](C:/Users/s0302/OneDrive/바탕%20화면/portpolio/dis/build.gradle)에는 아래 두 런타임 네이티브를 함께 포함했다.

- `natives-win-x86-64`
- `natives-linux-musl-x86-64`

의미는 아래와 같다.

- 로컬 Windows 실행 유지
- Docker Alpine 배포 이미지에서 음성 재생 가능성 확보

## 3. Command DLQ 재처리

### 개요

RabbitMQ command consumer는 실패한 명령을 DLQ로 보낸다.

- 메인 큐: `messaging.command-queue`
- DLQ: `messaging.command-dead-letter-queue`

재처리는 애플리케이션의 운영 모드를 통해 수행한다.

### 동작 방식

DLQ 재처리 모드가 켜지면 아래 순서로 동작한다.

1. DLQ에서 메시지를 하나씩 읽는다.
2. JSON body를 `MusicCommandMessage`로 역직렬화한다.
3. 메인 command exchange로 다시 발행한다.
4. 발행이 성공하면 DLQ 메시지를 ack 한다.
5. 발행이 실패하면 해당 메시지를 다시 DLQ로 requeue 하고 중단한다.

이 모드에서는 일반 서비스 기동과 충돌하지 않도록 아래 기능을 비활성화했다.

- JDA 세션 기동
- command consumer 기동
- event outbox relay 기동

### 서버에서 실행하는 방법

서버에는 [ops/replay-command-dlq.sh](C:/Users/s0302/OneDrive/바탕%20화면/portpolio/dis/ops/replay-command-dlq.sh)를 함께 배포한다.

기본 사용법:

```bash
bash /home/ubuntu/dis-bot/current/ops/replay-command-dlq.sh
```

최대 재처리 개수 지정:

```bash
bash /home/ubuntu/dis-bot/current/ops/replay-command-dlq.sh 100
```

이 스크립트는 내부적으로 `audio-node` 컨테이너 이미지를 재사용해 아래 운영 옵션으로 1회성 실행을 수행한다.

- `--ops.command-dlq-replay-enabled=true`
- `--ops.command-dlq-replay-exit-after-run=true`
- `--ops.command-dlq-replay-max-messages=<개수>`

## 4. 배포 직후 스모크 체크

### 자동 확인

서버에는 [ops/smoke-check.sh](C:/Users/s0302/OneDrive/바탕%20화면/portpolio/dis/ops/smoke-check.sh)를 함께 배포한다.

```bash
bash /home/ubuntu/dis-bot/current/ops/smoke-check.sh
```

이 스크립트는 아래를 확인한다.

- `docker compose ps`
- `http://127.0.0.1:8081/actuator/health`
- `http://127.0.0.1:8082/actuator/health`

### 수동 확인

자동 확인 뒤에는 Discord에서 아래를 직접 확인해야 한다.

1. slash command가 보이는지 확인
2. `/join` 응답 확인
3. `/play`로 실제 재생 확인
4. `/skip`, `/stop` 응답 확인
5. 컨테이너 재시작 후 recovery 동작 확인

## 5. 이중 JDA 세션 검증 포인트

현재 구조는 `gateway`와 `audio-node`가 같은 봇 토큰으로 각각 JDA 세션을 연다.

코드상 역할 분리는 되어 있다.

- `gateway`
  - slash command 수신
  - command 등록
- `audio-node`
  - recovery
  - playback 처리
  - RabbitMQ consumer

운영에서 반드시 확인할 항목은 아래다.

1. slash command 응답이 중복되지 않는지
2. `audio-node` ready 이후 recovery가 한 번만 수행되는지
3. voice join / play / stop이 정상 동작하는지
4. 재배포 후 세션 reconnect 과정에서 명령 누락이 없는지

## 6. 현재 기준 남은 작업

이번 런북 반영 이후에도 완전히 끝난 것은 아니다.

- 실제 Discord 운영 환경에서 이중 JDA 세션을 실배포 검증
- command DLQ 재처리 운영 빈도와 실패 패턴 관찰
- observability 스택 추가 여부 결정
