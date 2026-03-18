# Discord Music Bot

현재 저장소는 `gateway-app`, `audio-node-app`, `common-core` 3축으로 정리되어 있다.

핵심 원칙은 단순하다.

- Discord 명령 진입점은 `gateway-app`
- 실제 재생과 recovery는 `audio-node-app`
- 공용 도메인과 재생 코어는 `common-core`
- 상태 저장은 Redis만 사용
- 앱 간 명령 전달은 RabbitMQ만 사용
- 로컬 fallback 저장소와 in-process bus는 제거

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

## 모듈 설명

### `apps/gateway-app`

- slash command 수신
- autocomplete 처리
- command 생성
- RabbitMQ command producer

자세한 설명:

- [gateway-app README](/C:/Users/s0302/OneDrive/바탕%20화면/portpolio/dis/apps/gateway-app/README.md)

### `apps/audio-node-app`

- RabbitMQ command consumer
- 음성 채널 연결
- 실제 트랙 재생
- playback recovery

자세한 설명:

- [audio-node-app README](/C:/Users/s0302/OneDrive/바탕%20화면/portpolio/dis/apps/audio-node-app/README.md)

### `modules/common-core`

- command / event 모델
- worker 로직
- 재생 엔진
- Redis 저장소
- RabbitMQ command 인프라

자세한 설명:

- [common-core README](/C:/Users/s0302/OneDrive/바탕%20화면/portpolio/dis/modules/common-core/README.md)

## 빠른 실행

전체 JAR 생성:

```powershell
.\gradlew.bat bootJarAll
```

gateway만 실행:

```powershell
java -jar apps/gateway-app/build/libs/gateway-app.jar
```

audio-node만 실행:

```powershell
java -jar apps/audio-node-app/build/libs/audio-node-app.jar
```

로컬 compose 실행:

```powershell
docker compose up -d --build
```

관측성 스택 포함 실행:

```powershell
docker compose --profile observability up -d --build
```

## 문서

- [문서 인덱스](/C:/Users/s0302/OneDrive/바탕%20화면/portpolio/dis/docs/README.md)
- [현재 아키텍처](/C:/Users/s0302/OneDrive/바탕%20화면/portpolio/dis/docs/CURRENT_ARCHITECTURE.md)
- [코드베이스 분석](/C:/Users/s0302/OneDrive/바탕%20화면/portpolio/dis/docs/CODEBASE_ANALYSIS.md)
- [관측성 계획](/C:/Users/s0302/OneDrive/바탕%20화면/portpolio/dis/docs/OBSERVABILITY_PLAN.md)
- [이벤트 계약](/C:/Users/s0302/OneDrive/바탕%20화면/portpolio/dis/docs/EVENT_CONTRACT.md)
- [운영 런북](/C:/Users/s0302/OneDrive/바탕%20화면/portpolio/dis/docs/OPERATIONS_RUNBOOK.md)
- [배포 스크립트 가이드](/C:/Users/s0302/OneDrive/바탕%20화면/portpolio/dis/docs/SERVER_DEPLOY_SCRIPT.md)
- [작업 로그](/C:/Users/s0302/OneDrive/바탕%20화면/portpolio/dis/docs/CODEX_WORK_LOG.md)
- [관측성 스택 설정](/C:/Users/s0302/OneDrive/바탕%20화면/portpolio/dis/ops/observability/README.md)

## 관측성 상태

현재 구조는 아래까지 반영된 상태다.

- `health`, `info`, `prometheus` Actuator endpoint 노출
- 콘솔 로그 ECS JSON 구조화
- `commandId`, `correlationId`, `producer`, `schemaVersion` MDC 전파

다음 단계는 `Grafana + Prometheus + Loki + Alloy` compose 스택 추가다.

## 현재 기준 정리

이번 정리 이후 남아 있는 것은 아래뿐이다.

- 실제 소스 코드
- 실행에 필요한 설정과 배포 파일
- 운영에 필요한 스크립트
- 현재 구조를 설명하는 문서

빌드 산출물, IDE 메타파일, 사용하지 않는 fallback 구현, 쓰지 않는 이벤트 outbox 경로는 제거했다.
