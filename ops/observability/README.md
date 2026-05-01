# Observability Stack

이 디렉터리는 현재 observability stack 설정을 모아 둔다.

구성 요소:

- `prometheus/`
- `loki/`
- `alloy/`
- `grafana/`

## 현재 포함 범위

- gateway-app metrics
- audio-node-app metrics
- Redis metrics
- RabbitMQ metrics
- Loki metrics
- Alloy metrics
- structured logs

현재 `stock-node-app` metrics scrape는 아직 포함되지 않았다.

## 실행

```powershell
docker compose --profile observability up -d
```

중지:

```powershell
docker compose --profile observability stop prometheus loki alloy redis-exporter grafana
```

## 접근 주소

- Grafana: `http://localhost:3000`
- Prometheus: `http://localhost:9090`
- Loki: `http://localhost:3100`
- Alloy UI: `http://localhost:12345`

## Grafana 기본 계정

- `GRAFANA_ADMIN_USER`
- `GRAFANA_ADMIN_PASSWORD`

주의:

- Grafana admin 계정은 최초 볼륨 생성 시점에만 env가 반영된다.

## 기본 provision

datasource:

- Prometheus
- Loki

dashboard:

- `Discord Bot App Overview`
- `Discord Bot Infra Overview`

contact point:

- `observability-noop`
- `observability-discord`
