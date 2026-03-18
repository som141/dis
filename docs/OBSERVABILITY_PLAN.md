# 옵저버빌리티 계획

## 1. 현재 상태

옵저버빌리티는 더 이상 계획만 있는 상태가 아니다. 현재 저장소에는 아래가 이미 구현되어 있다.

- `/actuator/prometheus` 노출
- ECS JSON structured logging
- `commandId`, `correlationId` 기반 MDC 전파
- `prometheus`, `loki`, `alloy`, `redis-exporter`, `grafana` compose 구성
- Grafana datasource provisioning
- Grafana dashboard provisioning
- Prometheus alert rules
- Grafana-managed alert rules
- Discord webhook 기반 contact point provisioning
- 원격 CI/CD에서 `OBSERVABILITY_ENABLED=true`일 때 관측성 스택 자동 기동

즉 현재 문서의 목적은 “무엇을 도입할지”보다 “무엇이 이미 들어갔고 다음에 무엇을 할지”를 정리하는 것이다.

## 2. 현재 채택한 조합

- 로그: Loki
- 로그 수집기: Alloy
- 메트릭: Prometheus
- 대시보드 / 알림: Grafana
- 추적: 아직 미도입

## 3. 왜 이 조합인가

- Docker Compose 환경에 바로 붙이기 쉽다.
- Spring Boot Actuator와 Prometheus scrape 모델이 잘 맞는다.
- stdout 기반 structured logging과 Alloy 조합이 단순하다.
- 현재 규모에서는 ELK보다 Loki가 운영 복잡도를 낮춘다.

Promtail은 새로 도입하지 않는다. 현재 기준으로는 Alloy를 collector로 쓰는 쪽이 맞다.

## 4. 현재 구현 범위

### 메트릭

- `gateway-app` actuator metrics
- `audio-node-app` actuator metrics
- `redis-exporter`
- RabbitMQ Prometheus endpoint
- Prometheus 자체 metrics
- Loki metrics
- Alloy metrics

### 로그

- gateway stdout
- audio-node stdout
- ECS structured logging
- `music-command`, `music-event`, `startup-config` 구조 로그

### 대시보드

- `Discord Bot App Overview`
- `Discord Bot Infra Overview`

### Prometheus alert rules

- `GatewayDown`
- `AudioNodeDown`
- `RedisExporterDown`
- `RabbitMqExporterDown`
- `AppHighJvmHeapUsage`
- `ApplicationErrorLogsDetected`
- `RabbitMqConsumerMissing`
- `RabbitMqQueueBacklog`
- `RabbitMqUnackedBacklog`

### Grafana-managed alert rules

- `GatewayDownGrafana`
- `AudioNodeDownGrafana`
- `AppHighJvmHeapUsageGrafana`
- `RabbitMqConsumerMissingGrafana`

### Contact points

- `observability-noop`
- `observability-discord`

기본 수신자는 `observability-noop`이다.

## 5. 현재 환경변수

관측성 관련 핵심 변수:

- `OBSERVABILITY_ENABLED`
- `GRAFANA_ADMIN_USER`
- `GRAFANA_ADMIN_PASSWORD`
- `GRAFANA_ANONYMOUS_ENABLED`
- `GRAFANA_ALERT_DEFAULT_RECEIVER`
- `GRAFANA_ALERT_NOOP_WEBHOOK_URL`
- `GRAFANA_ALERT_DISCORD_WEBHOOK_URL`
- `GRAFANA_ALERT_DISCORD_AVATAR_URL`
- `GRAFANA_ALERT_DISCORD_USE_USERNAME`
- `GRAFANA_IMAGE`
- `PROMETHEUS_IMAGE`
- `LOKI_IMAGE`
- `ALLOY_IMAGE`
- `REDIS_EXPORTER_IMAGE`

## 6. 현재 남은 작업

### 1순위

- 실제 Discord webhook secret 연결
- `GRAFANA_ALERT_DEFAULT_RECEIVER=observability-discord` 운영 전환
- 운영 서버에서 Grafana 로그인 계정 최종 정리

### 2순위

- Loki 기반 로그 알림 추가
- 봇 전용 비즈니스 메트릭 추가
  - `music_commands_total`
  - `music_command_duration_seconds`
  - `music_track_load_failures_total`
  - `music_recovery_attempts_total`

### 3순위

- OpenTelemetry + Tempo 도입
- `gateway-app -> RabbitMQ -> audio-node-app` trace 연결

## 7. 운영상 주의점

- Grafana 관리자 계정은 최초 기동 시점에만 env가 반영된다.
- 기존 `grafana-data` 볼륨이 있으면 계정 변경은 `grafana cli admin reset-admin-password`나 볼륨 초기화로 처리해야 한다.
- `OBSERVABILITY_ENABLED=false`면 원격 배포 시 관측성 컨테이너는 올라오지 않는다.

## 8. 결론

현재 저장소의 관측성은 “기반 설계만 있음” 단계가 아니라, 대시보드/알림/원격 배포 연동까지 끝난 상태다. 이제 남은 것은 실제 운영 채널 연동과 trace 확장이다.
