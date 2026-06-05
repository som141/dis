# 주식 시스템 8주차 관측성 보강 작업 보고서

## 1. 목적

이번 8주차 작업의 목적은 7주차에서 stock-node를 Prometheus scrape 대상에 포함한 뒤 남아 있던 관측성 공백을 줄이는 것이다.

기준은 아래와 같았다.

- 기존 `gateway-app`, `audio-node-app`, `stock-node-app` 구조를 변경하지 않는다.
- Discord 알림에 사용되는 Grafana-managed alert에도 stock-node 알림을 반영한다.
- Redis quote cache 상태를 metric으로 확인할 수 있게 한다.
- PostgreSQL을 exporter 기반 관측성 대상에 포함한다.
- dashboard, alert, 운영 문서를 현재 compose 설정과 일치시킨다.
- 테스트와 설정 유효성 검증을 함께 수행한다.

## 2. 이번 주차에서 완료한 항목

### 2.1 Redis quote cache gauge 추가

`StockMetricsRecorder`에 Redis quote cache 상태 gauge를 추가했다.

추가 metric:

- `stock_quote_cache_expected`
- `stock_quote_cache_ready`
- `stock_quote_cache_stale`
- `stock_quote_cache_oldest_age`

이 metric은 시장 단위 label만 사용한다.

- `market`

`guildId`, `userId`, `commandId`는 metric label에 넣지 않았다.

### 2.2 quote cache 상태 계산 서비스 추가

`StockQuoteCacheMetricsService`를 추가했다.

역할:

- watchlist 전체 종목 수 계산
- Redis quote cache에 준비된 종목 수 계산
- freshness 기준보다 오래된 stale quote 수 계산
- 가장 오래된 quote age 계산

`FinnhubTop10RefreshScheduler`는 20초 refresh loop가 끝난 뒤 이 서비스를 호출한다.

### 2.3 Grafana-managed alert에 stock-node 반영

기존 Prometheus rule에는 stock-node alert가 있었지만, Discord 알림에 실제로 연결되는 Grafana-managed alert에는 stock-node rule이 빠져 있었다.

이번 작업에서 `ops/observability/grafana/provisioning/alerting/alert-rules.yml`을 현재 운영 기준으로 정리하고 아래 rule을 추가했다.

- `StockNodeDownGrafana`
- `StockQuoteRefreshStalledGrafana`
- `StockQuoteRefreshFailuresHighGrafana`
- `StockQuoteCacheNotReadyGrafana`
- `StockQuoteCacheStaleGrafana`
- `StockTradeRejectionsHighGrafana`

또한 `AppHighJvmHeapUsageGrafana`가 `gateway`, `audio-node`, `stock-node`를 모두 보도록 수정했다.

### 2.4 PostgreSQL exporter 추가

`docker-compose.yml`에 `postgres-exporter` 서비스를 추가했다.

추가 서비스:

- `postgres-exporter`

노출 포트:

- `9187`

Prometheus scrape job도 추가했다.

- `job="postgres-exporter"`

### 2.5 PostgreSQL alert 추가

Prometheus rule에 PostgreSQL 관련 alert를 추가했다.

- `PostgresExporterDown`
- `PostgresDown`
- `PostgresConnectionsHigh`

Grafana-managed alert에도 아래 rule을 추가했다.

- `PostgresExporterDownGrafana`
- `PostgresDownGrafana`

### 2.6 Grafana dashboard 보강

기존 app dashboard가 `gateway|audio-node`만 보던 부분을 `gateway|audio-node|stock-node`로 확장했다.

수정 대상:

- app status
- JVM heap usage
- process CPU usage
- live threads
- warn/error log rate

stock-node dashboard에는 quote cache gauge panel을 추가했다.

- quote cache ready ratio
- quote cache oldest age

PostgreSQL 전용 dashboard도 추가했다.

- `discord-bot-postgres-overview.json`

확인 항목:

- PostgreSQL 상태
- connection count
- database size
- transaction commit/rollback rate

### 2.7 문서 정리

아래 문서를 현재 상태에 맞게 수정했다.

- `README.md`
- `docs/reference/CURRENT_ARCHITECTURE.md`
- `docs/operations/OBSERVABILITY_PLAN.md`
- `docs/operations/OPERATIONS_RUNBOOK.md`
- `ops/observability/README.md`
- `ops/observability/grafana/dashboards/README.md`
- `docs/internal/stock-history/README.md`

특히 오래된 문구였던 “stock-node metrics scrape 미포함” 설명을 제거하고, PostgreSQL exporter와 quote cache gauge를 반영했다.

## 3. 이번 주차에 추가하거나 수정한 주요 파일

### 수정한 파일

- `README.md`
- `docker-compose.yml`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/FinnhubTop10RefreshScheduler.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/config/StockNodeComponentConfiguration.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/observability/StockMetricsRecorder.java`
- `apps/stock-node-app/src/test/java/discordgateway/stocknode/application/FinnhubTop10RefreshSchedulerTest.java`
- `apps/stock-node-app/src/test/java/discordgateway/stocknode/observability/StockMetricsRecorderTest.java`
- `docs/operations/OBSERVABILITY_PLAN.md`
- `docs/operations/OPERATIONS_RUNBOOK.md`
- `docs/reference/CURRENT_ARCHITECTURE.md`
- `ops/observability/README.md`
- `ops/observability/grafana/dashboards/README.md`
- `ops/observability/grafana/dashboards/discord-bot-app-overview.json`
- `ops/observability/grafana/dashboards/discord-bot-infra-overview.json`
- `ops/observability/grafana/dashboards/discord-bot-stock-node-overview.json`
- `ops/observability/grafana/provisioning/alerting/alert-rules.yml`
- `ops/observability/prometheus/alerts.yml`
- `ops/observability/prometheus/prometheus.yml`

### 새로 추가한 파일

- `apps/stock-node-app/src/main/java/discordgateway/stocknode/observability/StockQuoteCacheMetricsService.java`
- `apps/stock-node-app/src/test/java/discordgateway/stocknode/observability/StockQuoteCacheMetricsServiceTest.java`
- `ops/observability/grafana/dashboards/discord-bot-postgres-overview.json`
- `docs/internal/stock-history/STOCK_WEEK8_OBSERVABILITY_REPORT.md`

## 4. 검증 결과

### 4.1 컴파일 검증

실행 명령:

```powershell
.\gradlew.bat compileJava compileTestJava
```

결과:

- 성공

### 4.2 관측성 관련 targeted test

한글/공백이 없는 ASCII 경로에서 실행했다.

실행 명령:

```powershell
.\gradlew.bat :apps:stock-node-app:test --tests "*StockMetricsRecorderTest" --tests "*StockQuoteCacheMetricsServiceTest" --tests "*FinnhubTop10RefreshSchedulerTest"
```

결과:

- 성공

검증 항목:

- stock business counter 기록
- quote cache gauge 기록
- watchlist 기준 cache readiness/staleness 계산
- provider가 mock일 때 scheduler 미실행
- Finnhub refresh 중 일부 종목 실패 시 나머지 처리 지속
- refresh 후 quote cache metric 갱신 호출

### 4.3 dashboard JSON 유효성 검증

실행한 검증:

```powershell
python -c "import json, pathlib; [json.loads(p.read_text(encoding='utf-8')) for p in pathlib.Path('ops/observability/grafana/dashboards').glob('*.json')]"
```

결과:

- 성공

검증 대상:

- `discord-bot-app-overview.json`
- `discord-bot-infra-overview.json`
- `discord-bot-stock-node-overview.json`
- `discord-bot-postgres-overview.json`

### 4.4 docker compose 설정 검증

실행 명령:

```powershell
docker compose config --quiet
```

결과:

- 성공

### 4.5 로컬 경로 주의사항

Windows의 한글/공백 포함 OneDrive 경로에서는 Gradle test classpath 문제가 발생할 수 있다.

이번에도 해당 경로에서 targeted test 실행 시 `ClassNotFoundException` 계열 문제가 발생했지만, 같은 코드가 ASCII 경로에서는 정상 통과했다.

따라서 코드 실패가 아니라 로컬 실행 경로 문제로 판단했다.

## 5. 이번 주차 완료 기준에 대한 판단

이번 작업으로 아래 기준이 충족되었다.

- stock-node Grafana-managed alert 추가
- stock-node JVM heap alert 범위 확장
- Redis quote cache 상태 metric 추가
- quote cache stale/not-ready alert 추가
- PostgreSQL exporter compose 서비스 추가
- PostgreSQL Prometheus scrape job 추가
- PostgreSQL dashboard 추가
- PostgreSQL alert 추가
- app dashboard에 stock-node 포함
- 운영 문서와 관측성 문서 최신화
- 컴파일 및 targeted test 통과
- dashboard JSON과 compose config 검증 통과

## 6. 남은 후속 작업

아직 남은 확장 후보는 아래와 같다.

- OpenTelemetry + Tempo trace 도입
- Gateway -> RabbitMQ -> Stock Node command trace
- Gateway -> RabbitMQ -> Audio Node command trace
- PostgreSQL slow query / lock dashboard 세분화
- stock account / position aggregate gauge
- 실제 운영 서버에서 Grafana alert firing/resolved 메시지 확인

## 7. 결론

이번 8주차 작업으로 stock-node 관측성은 단순 scrape 수준에서 운영 가능한 business metric, cache 상태 metric, Grafana-managed alert, PostgreSQL exporter까지 확장되었다.

현재 상태는 stock-node의 핵심 장애 신호를 Discord 알림으로 받을 수 있고, Redis quote cache와 PostgreSQL 상태를 Grafana dashboard에서 확인할 수 있는 구조다.
