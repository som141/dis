# Grafana Dashboards

이 디렉터리의 JSON 파일은 Grafana file provisioning으로 자동 로드된다.

현재 포함된 대시보드:

- `discord-bot-app-overview.json`
  - gateway-app, audio-node-app 중심
  - health, JVM, request, 로그 관련 패널
- `discord-bot-infra-overview.json`
  - Redis, RabbitMQ, observability stack 중심
  - queue backlog, exporter, scrape 상태 패널

대시보드는 `ops/observability/grafana/provisioning/dashboards/dashboards.yml`을 통해 자동 로드된다.
