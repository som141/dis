# 관측성 계획

## 현재 스택

- Prometheus
- Loki
- Alloy
- Grafana
- redis-exporter
- postgres-exporter

로그는 Alloy가 Docker stdout을 수집해 Loki로 보낸다. 메트릭은 Prometheus가 scrape한다. 대시보드와 알림은 Grafana provisioning으로 로드한다.

## 현재 scrape 대상

- `gateway`
- `audio-node`
- `stock-node`
- `redis-exporter`
- `postgres-exporter`
- `rabbitmq`
- `prometheus`
- `loki`
- `alloy`

`gateway-app`, `audio-node-app`, `stock-node-app`은 `/actuator/prometheus`를 노출한다.

## stock-node 관측성

stock-node에서 확인하는 주요 지표:

- JVM heap / process / actuator metric
- stock command 처리 성공/실패와 duration
- Finnhub quote refresh 성공/실패
- Redis quote cache 준비 수량, stale 수량, 가장 오래된 quote age
- provider rate limit 초과
- 거래 체결과 거래 거절
- 자동 청산 발생 수

대표 Prometheus query:

```promql
up{job="stock-node"}
stock_quote_refresh_success_total
stock_quote_refresh_failures_total
stock_quote_cache_ready
stock_quote_cache_expected
stock_quote_cache_stale
stock_quote_cache_oldest_age
stock_trade_executions_total
stock_trade_rejections_total
stock_auto_liquidations_total
```

## PostgreSQL 관측성

PostgreSQL은 `postgres-exporter`로 scrape한다.

대표 Prometheus query:

```promql
up{job="postgres-exporter"}
pg_up{job="postgres-exporter"}
sum by(datname, state) (pg_stat_activity_count{job="postgres-exporter"})
pg_database_size_bytes{job="postgres-exporter"}
rate(pg_stat_database_xact_commit{job="postgres-exporter"}[5m])
rate(pg_stat_database_xact_rollback{job="postgres-exporter"}[5m])
```

## 대시보드

Grafana dashboard는 file provisioning으로 로드된다.

- `discord-bot-app-overview.json`
- `discord-bot-infra-overview.json`
- `discord-bot-stock-node-overview.json`
- `discord-bot-postgres-overview.json`

## 알림

Grafana-managed alert rule:

- `GatewayDownGrafana`
- `AudioNodeDownGrafana`
- `StockNodeDownGrafana`
- `AppHighJvmHeapUsageGrafana`
- `StockQuoteRefreshStalledGrafana`
- `StockQuoteRefreshFailuresHighGrafana`
- `StockQuoteCacheNotReadyGrafana`
- `StockQuoteCacheStaleGrafana`
- `StockTradeRejectionsHighGrafana`
- `PostgresExporterDownGrafana`
- `PostgresDownGrafana`
- `RabbitMqConsumerMissingGrafana`

Prometheus rule 파일에도 동일 계열의 infrastructure/business alert를 둔다.

운영 전환 시 Discord 알림은 아래 env로 켠다.

```env
GRAFANA_ALERT_DEFAULT_RECEIVER=observability-discord
GRAFANA_ALERT_DISCORD_WEBHOOK_URL=...
```

## 남은 확장 후보

- OpenTelemetry + Tempo trace
- gateway -> RabbitMQ -> stock-node command trace
- gateway -> RabbitMQ -> audio-node command trace
- stock account/position aggregate gauge
- PostgreSQL slow query/lock 세부 dashboard

## 관련 파일

- `ops/observability/prometheus/prometheus.yml`
- `ops/observability/prometheus/alerts.yml`
- `ops/observability/loki/loki-config.yml`
- `ops/observability/alloy/config.alloy`
- `ops/observability/grafana/dashboards/**`
- `ops/observability/grafana/provisioning/**`
