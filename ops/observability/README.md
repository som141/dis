# Observability Stack

이 디렉터리는 DIS Discord bot의 로컬/운영 관측성 설정을 모아 둔다.

구성 요소:

- `prometheus/`
- `loki/`
- `alloy/`
- `grafana/`

## 현재 포함 범위

- gateway-app metrics
- audio-node-app metrics
- stock-node-app metrics
- Redis metrics
- PostgreSQL metrics
- RabbitMQ metrics
- Prometheus/Loki/Alloy metrics
- Docker stdout 기반 structured logs

Prometheus scrape 대상:

- `gateway`
- `audio-node`
- `stock-node`
- `redis-exporter`
- `postgres-exporter`
- `rabbitmq`
- `prometheus`
- `loki`
- `alloy`

## 실행

```powershell
docker compose --profile observability up -d
```

중지:

```powershell
docker compose --profile observability stop prometheus loki alloy redis-exporter postgres-exporter grafana
```

## 접근 주소

- Grafana: `http://localhost:3000`
- Prometheus: `http://localhost:9090`
- Loki: `http://localhost:3100`
- Alloy UI: `http://localhost:12345`
- Redis exporter: `http://localhost:9121/metrics`
- PostgreSQL exporter: `http://localhost:9187/metrics`

## Grafana 기본 계정

- `GRAFANA_ADMIN_USER`
- `GRAFANA_ADMIN_PASSWORD`

Grafana admin 계정은 최초 volume 생성 시점에 반영된다. 이미 `grafana-data` volume이 있으면 계정 env를 바꿔도 기존 계정이 유지될 수 있다.

## 기본 provision

Datasource:

- Prometheus
- Loki

Dashboard:

- `Discord Bot App Overview`
- `Discord Bot Infra Overview`
- `Discord Bot Stock Node`
- `Discord Bot PostgreSQL Overview`

Contact point:

- `observability-noop`
- `observability-discord`
