# Observability Stack

이 디렉터리는 현재 저장소의 관측성 스택 설정을 모아둔 곳이다.

구성 요소:

- `prometheus/`
- `loki/`
- `alloy/`
- `grafana/`

## 현재 구성

- Prometheus
  - 앱, Redis, RabbitMQ, Alloy, Loki scrape
- Loki
  - 앱 구조 로그 저장
- Alloy
  - Docker stdout 로그 수집
- Grafana
  - datasource provisioning
  - dashboard provisioning
  - alerting provisioning
- redis-exporter
  - Redis metrics 노출

## 실행

```powershell
docker compose --profile observability up -d prometheus loki alloy redis-exporter grafana
```

중지:

```powershell
docker compose --profile observability stop prometheus loki alloy redis-exporter grafana
```

## 접속

- Grafana: `http://localhost:3000`
- Prometheus: `http://localhost:9090`
- Loki API: `http://localhost:3100`
- Alloy UI: `http://localhost:12345`

## Grafana 로그인

기본값은 `.env` 또는 `.env.example`의 아래 값이다.

- `GRAFANA_ADMIN_USER`
- `GRAFANA_ADMIN_PASSWORD`

주의:

- Grafana 관리자 계정은 첫 기동 시점에만 env가 적용된다.
- 기존 `grafana-data` 볼륨이 있으면 나중에 env를 바꿔도 로그인 계정이 자동으로 바뀌지 않는다.

## 기본 provision

### Datasource

- Prometheus
- Loki

### Dashboard

- `Discord Bot App Overview`
- `Discord Bot Infra Overview`

### Contact point

- `observability-noop`
- `observability-discord`

### Grafana-managed alert rule

- `GatewayDownGrafana`
- `AudioNodeDownGrafana`
- `AppHighJvmHeapUsageGrafana`
- `RabbitMqConsumerMissingGrafana`

### Prometheus alert rule

- gateway down
- audio-node down
- Redis exporter down
- RabbitMQ exporter down
- JVM heap high
- error log detected
- RabbitMQ consumer missing
- RabbitMQ queue backlog
- RabbitMQ unacked backlog

## 알림 활성화

기본 상태:

```env
GRAFANA_ALERT_DEFAULT_RECEIVER=observability-noop
```

Discord 전송 활성화:

```env
GRAFANA_ALERT_DEFAULT_RECEIVER=observability-discord
GRAFANA_ALERT_DISCORD_WEBHOOK_URL=<실제 Discord webhook URL>
```

## 확인 포인트

- Prometheus rules: `http://localhost:9090/rules`
- Prometheus alerts: `http://localhost:9090/alerts`
- Grafana dashboards: `Dashboards`
- Grafana alert rules: `Alerting`

## 원격 배포

원격 배포에서도 `OBSERVABILITY_ENABLED=true`면 이 디렉터리 전체가 release에 같이 업로드되고, `--profile observability`로 관측성 스택이 같이 올라간다.
