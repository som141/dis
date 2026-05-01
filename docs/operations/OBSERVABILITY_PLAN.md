# 관측성 계획

## 현재 스택

- Prometheus
- Loki
- Alloy
- Grafana
- redis-exporter

로그는 Alloy가 Docker stdout을 수집해 Loki로 보낸다. 메트릭은 Prometheus가 scrape한다. 대시보드와 알림은 Grafana가 담당한다.

## 현재 scrape 대상

- `gateway-app`
- `audio-node-app`
- `redis-exporter`
- `rabbitmq`
- `prometheus`
- `loki`
- `alloy`

중요:

- 현재 `stock-node-app`은 Prometheus scrape 대상이 아니다.
- 따라서 stock-node JVM/앱 메트릭은 아직 Grafana에서 직접 보이지 않는다.

## 현재 알림

Prometheus/Grafana 쪽에서 현재 다루는 핵심 알림은 다음 범주다.

- gateway down
- audio-node down
- Redis exporter down
- RabbitMQ exporter down
- JVM heap high
- application error logs
- RabbitMQ consumer missing
- queue backlog

## 현재 한계

- `stock-node` 메트릭이 scrape되지 않는다.
- trace 시스템이 없다.
- stock 거래/시세/랭킹 전용 메트릭이 없다.

## 다음 우선순위

### 1. stock-node scrape 추가

가장 먼저 할 일은 `stock-node:8080/actuator/prometheus`를 Prometheus scrape에 넣는 것이다.

추가되면 볼 수 있는 항목:

- stock command 처리량
- JVM/actuator health
- scheduler 실행 패턴

### 2. stock 비즈니스 메트릭 추가

예시:

- `stock_commands_total`
- `stock_command_duration_seconds`
- `stock_quote_refresh_total`
- `stock_quote_refresh_failures_total`
- `stock_trade_rejections_total`
- `stock_rank_requests_total`

### 3. stock 전용 알림 추가

예시:

- `stock-node down`
- `finnhub refresh stalled`
- `quote cache empty`
- `quote refresh failure spike`

### 4. trace 도입 검토

필요 시 `gateway -> RabbitMQ -> stock-node`와 `gateway -> RabbitMQ -> audio-node` 흐름을 OpenTelemetry로 잇는다.

## 운영 기준

- 로그는 Loki에서 본다.
- 메트릭은 Prometheus/Grafana에서 본다.
- Discord 웹훅 알림은 운영 전환 시 `observability-discord`를 활성화한다.

## 관련 파일

- `ops/observability/prometheus/prometheus.yml`
- `ops/observability/prometheus/alerts.yml`
- `ops/observability/loki/loki-config.yml`
- `ops/observability/alloy/config.alloy`
- `ops/observability/grafana/provisioning/**`
