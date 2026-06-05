# Grafana Dashboards

이 디렉터리의 JSON 파일은 Grafana file provisioning으로 자동 로드된다.

현재 dashboard:

- `discord-bot-app-overview.json`
  - gateway-app, audio-node-app, stock-node-app 공통 JVM/process/log 상태
- `discord-bot-infra-overview.json`
  - Redis, PostgreSQL exporter, RabbitMQ, Prometheus, Loki, Alloy scrape 상태
- `discord-bot-stock-node-overview.json`
  - stock-node quote refresh, Redis quote cache, command, trade, liquidation 상태
- `discord-bot-postgres-overview.json`
  - PostgreSQL 연결 상태, connection count, database size, transaction rate

대시보드는 `ops/observability/grafana/provisioning/dashboards/dashboards.yml`을 통해 자동 로드된다.
