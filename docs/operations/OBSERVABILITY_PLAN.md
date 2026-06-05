# 관측성 계획

## 현재 스택

- Prometheus
- Loki
- Alloy
- Grafana
- redis-exporter

로그는 Alloy가 Docker stdout을 수집해 Loki로 보낸다. 메트릭은 Prometheus가 scrape한다. 대시보드와 알림은 Grafana와 Prometheus rule이 담당한다.

## 현재 scrape 대상

- `gateway-app`
- `audio-node-app`
- `stock-node-app`
- `redis-exporter`
- `rabbitmq`
- `prometheus`
- `loki`
- `alloy`

`stock-node-app`은 `/actuator/prometheus`를 노출하며 Prometheus scrape 대상에 포함된다.

## stock-node 관측성

stock-node에서 확인하는 주요 지표는 다음과 같다.

- JVM heap / process / HTTP actuator metric
- stock command 처리량과 성공/실패
- Finnhub quote refresh 성공/실패
- provider rate limit 초과
- 거래 체결과 거래 거절
- 자동 청산 발생 수

비즈니스 메트릭은 Micrometer 기반으로 노출한다.

대표 Prometheus query:

```promql
up{job="stock-node"}
stock_quote_refresh_success_total
stock_quote_refresh_failures_total
stock_trade_executions_total
stock_trade_rejections_total
stock_auto_liquidations_total
```

## 대시보드

Grafana dashboard는 file provisioning으로 로드된다.

- `discord-bot-app-overview.json`
- `discord-bot-infra-overview.json`
- `discord-bot-stock-node-overview.json`

stock-node dashboard는 quote refresh, command, trade, liquidation 지표를 본다.

## 알림

현재 Prometheus rule 기준으로 아래 알림을 둔다.

- `GatewayDown`
- `AudioNodeDown`
- `StockNodeDown`
- `AppHighJvmHeapUsage`
- `ApplicationErrorLogsDetected`
- `StockQuoteRefreshStalled`
- `StockQuoteRefreshFailuresHigh`
- `StockTradeRejectionsHigh`
- `RedisExporterDown`
- `RabbitMqExporterDown`
- `RabbitMqConsumerMissing`
- `RabbitMqQueueBacklog`
- `RabbitMqUnackedBacklog`

운영 전환 시 Discord webhook receiver를 활성화한다.

## 남은 확장 후보

- OpenTelemetry + Tempo trace
- gateway -> RabbitMQ -> stock-node command trace
- gateway -> RabbitMQ -> audio-node command trace
- quote cache hit/miss gauge
- PostgreSQL exporter
- stock account/position aggregate gauge

## 관련 파일

- `ops/observability/prometheus/prometheus.yml`
- `ops/observability/prometheus/alerts.yml`
- `ops/observability/loki/loki-config.yml`
- `ops/observability/alloy/config.alloy`
- `ops/observability/grafana/dashboards/**`
- `ops/observability/grafana/provisioning/**`
