# 주식 시스템 7주차 관측성 작업 보고서

## 1. 목적

이번 7주차 작업의 목적은 `stock-node-app`을 기존 observability stack에 정식으로 포함하는 것이다.

기준은 아래와 같았다.

- 기존 `gateway-app`, `audio-node-app` 관측성 구성을 깨지 않는다.
- `stock-node-app`의 actuator metrics를 Prometheus scrape 대상에 포함한다.
- stock quote refresh, command, trade, liquidation 흐름을 비즈니스 메트릭으로 노출한다.
- Grafana dashboard와 Prometheus alert rule을 추가한다.
- 운영 문서와 smoke check를 현재 상태에 맞게 갱신한다.

## 2. 완료한 항목

### 2.1 Prometheus scrape 연결

`ops/observability/prometheus/prometheus.yml`에 `stock-node` scrape job을 추가했다.

추가된 target:

- `stock-node:8080/actuator/prometheus`

`docker-compose.yml`의 `prometheus.depends_on`에도 `stock-node`를 추가했다.

### 2.2 stock 비즈니스 메트릭 추가

`StockMetricsRecorder`를 추가해 Micrometer 기반 비즈니스 메트릭을 기록하도록 했다.

추가된 대표 메트릭:

- `stock_commands_total`
- `stock_command_duration_seconds`
- `stock_quote_refresh_success_total`
- `stock_quote_refresh_failures_total`
- `stock_provider_rate_limit_exceeded_total`
- `stock_trade_executions_total`
- `stock_trade_rejections_total`
- `stock_auto_liquidations_total`

label에는 `guildId`, `userId`, `commandId`를 넣지 않았다. cardinality가 큰 값은 로그로 남기고, metric label은 `command`, `result`, `market`, `symbol`, `side`, `provider`, `reason` 수준으로 제한했다.

### 2.3 계측 지점

계측을 추가한 지점은 아래와 같다.

- `StockCommandApplicationService`
  - command 성공/실패
  - command duration
  - buy/sell 실패 시 trade rejection
- `MarketQuoteRefreshService`
  - quote refresh 성공
  - quote refresh 실패
  - provider rate limit 초과
- `TradeExecutionService`
  - buy/sell 체결 성공
- `AutoLiquidationService`
  - 자동 청산 발생 수

### 2.4 Grafana dashboard 추가

stock-node 전용 dashboard를 추가했다.

파일:

- `ops/observability/grafana/dashboards/discord-bot-stock-node-overview.json`

패널:

- stock-node status
- JVM heap usage
- quote refresh success/failure rate
- stock command rate
- trade execution/rejection rate
- auto liquidation rate

### 2.5 Prometheus alert rule 추가

`ops/observability/prometheus/alerts.yml`을 현재 상태 기준으로 정리하고 stock alert rule을 추가했다.

추가/확장된 alert:

- `StockNodeDown`
- `StockQuoteRefreshStalled`
- `StockQuoteRefreshFailuresHigh`
- `StockTradeRejectionsHigh`
- `AppHighJvmHeapUsage`에 `stock-node` 포함
- `ApplicationErrorLogsDetected`에 `stock-node` 포함

### 2.6 smoke check 갱신

`ops/smoke-check.sh`에 stock-node metrics endpoint 확인을 추가했다.

확인 대상:

- `http://127.0.0.1:8083/actuator/health`
- `http://127.0.0.1:8083/actuator/prometheus`

### 2.7 문서 갱신

현재 상태에 맞춰 아래 문서를 갱신했다.

- `README.md`
- `docs/reference/CURRENT_ARCHITECTURE.md`
- `docs/operations/OBSERVABILITY_PLAN.md`
- `docs/operations/OPERATIONS_RUNBOOK.md`
- `ops/observability/grafana/dashboards/README.md`

## 3. 검증 결과

### 3.1 컴파일 검증

ASCII 경로에서 아래 명령을 실행했다.

```powershell
.\gradlew.bat compileJava compileTestJava
```

결과:

- 성공

### 3.2 관측성 관련 단위 테스트

ASCII 경로에서 아래 테스트를 실행했다.

```powershell
.\gradlew.bat :apps:stock-node-app:test --tests "*StockMetricsRecorderTest" --tests "*MarketQuoteRefreshServiceTest" --tests "*StockCommandApplicationServiceTest" --tests "*TradeExecutionServiceTest" --tests "*AutoLiquidationServiceTest"
```

결과:

- 성공

### 3.3 패키징 검증

ASCII 경로에서 아래 명령을 실행했다.

```powershell
.\gradlew.bat bootJarAll
```

결과:

- 성공

### 3.4 전체 stock-node 테스트 상태

아래 명령은 현재 로컬 Docker/Testcontainers 환경 문제로 통합 테스트 5개가 실패했다.

```powershell
.\gradlew.bat :apps:stock-node-app:test
```

실패 범위:

- `StockMessagingIntegrationTest`
- `StockPersistenceIntegrationTest`
- `StockRankingIntegrationTest`
- `StockRedisIntegrationTest`
- `StockTradingIntegrationTest`

원인:

- Docker/Testcontainers bootstrap 실패

관측성 변경으로 인한 단위 테스트 실패는 확인되지 않았다.

## 4. 완료 기준 판단

이번 작업으로 아래 기준을 충족했다.

- Prometheus scrape 설정에 `stock-node` 포함
- stock-node business metric 추가
- stock-node Grafana dashboard 추가
- stock-node alert rule 추가
- smoke check에 stock-node metrics 확인 추가
- 현재 아키텍처/운영 문서 갱신

남은 운영 확인:

- 실제 observability profile 기동 후 Prometheus target에서 `stock-node`가 `UP`인지 확인
- Grafana에서 `Discord Bot Stock Node` dashboard가 로드되는지 확인
- Finnhub mode에서 `stock_quote_refresh_success_total`이 증가하는지 확인

## 5. 운영 확인 명령

```bash
docker compose --profile observability up -d prometheus grafana loki alloy redis-exporter stock-node
```

```promql
up{job="stock-node"}
stock_quote_refresh_success_total
stock_trade_executions_total
stock_auto_liquidations_total
```

```bash
curl http://127.0.0.1:8083/actuator/prometheus | grep 'stock_'
```
