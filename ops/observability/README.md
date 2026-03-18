# Observability Stack

로컬과 개발 서버에서 바로 띄울 수 있는 관측성 스택 설정 모음이다.

구성 요소:

- `prometheus/`
- `loki/`
- `alloy/`
- `grafana/`

기동:

```powershell
docker compose --profile observability up -d prometheus loki alloy redis-exporter grafana
```

중지:

```powershell
docker compose --profile observability stop prometheus loki alloy redis-exporter grafana
```

접속:

- Grafana: `http://localhost:3000`
- Prometheus: `http://localhost:9090`
- Loki API: `http://localhost:3100`
- Alloy UI: `http://localhost:12345`

기본 Grafana 계정:

- ID: `admin`
- Password: `.env` 또는 `.env.example`의 `GRAFANA_ADMIN_PASSWORD`

기본 프로비저닝:

- Datasource
  - Prometheus
  - Loki
- Dashboard
  - `Discord Bot App Overview`
  - `Discord Bot Infra Overview`
- Grafana alerting
  - `GatewayDownGrafana`
  - `AudioNodeDownGrafana`
  - `AppHighJvmHeapUsageGrafana`
  - `RabbitMqConsumerMissingGrafana`
  - Contact point: `observability-noop`, `observability-discord`
- Prometheus alert rules
  - gateway down
  - audio-node down
  - Redis / RabbitMQ exporter down
  - JVM heap high
  - error log detected
  - RabbitMQ consumer missing / queue backlog

확인 포인트:

- Prometheus rules: `http://localhost:9090/rules`
- Prometheus alerts: `http://localhost:9090/alerts`
- Grafana 대시보드 검색: 좌측 메뉴 `Dashboards`
- Grafana alert rules: `http://localhost:3000/alerting/list`

알림 실제 전송 활성화:

- 기본값은 `GRAFANA_ALERT_DEFAULT_RECEIVER=observability-noop` 이다.
- `observability-noop` 는 더미 webhook 으로 흘려보내는 안전 기본값이다.
- Discord 전송을 켜려면 아래 두 값을 같이 설정한다.
  - `GRAFANA_ALERT_DEFAULT_RECEIVER=observability-discord`
  - `GRAFANA_ALERT_DISCORD_WEBHOOK_URL=<실제 Discord webhook URL>`

구조 정리:

- Prometheus alert rules 는 관찰과 빠른 상태 확인용이다.
- 실제 알림 전송 라우팅은 Grafana-managed alert rules 가 담당한다.
