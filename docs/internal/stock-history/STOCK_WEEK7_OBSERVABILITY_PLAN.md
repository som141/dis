# 주식 시스템 7주차 관측성 계획

## 1. 목적

7주차 작업의 목적은 `stock-node-app`을 기존 observability stack에 정식으로 편입하는 것이다.

현재 `stock-node-app`은 Spring Boot Actuator와 `/actuator/prometheus` 엔드포인트를 갖고 있지만, Prometheus scrape 대상에는 포함되어 있지 않다. Alloy가 Docker 로그를 수집하므로 로그는 Loki로 들어가지만, stock-node의 JVM 메트릭, health, quote refresh, 거래/청산 관련 지표는 Grafana에서 직접 볼 수 없다.

이번 주차에서는 다음을 목표로 한다.

- `stock-node-app` Prometheus scrape 추가
- stock 비즈니스 메트릭 추가
- stock-node Grafana dashboard 추가
- stock-node alert rule 추가
- smoke check와 운영 문서 갱신

## 2. 현재 상태

### 2.1 이미 구현된 것

- `stock-node-app`은 `spring-boot-starter-actuator`를 사용한다.
- `stock-node-app`은 `management.endpoints.web.exposure.include=health,info,prometheus`를 설정한다.
- `stock-node-app`은 compose에서 `8083:8080`으로 노출된다.
- Alloy는 Docker 로그를 수집하므로 `stock-node-app` 로그는 Loki로 전달된다.

### 2.2 아직 없는 것

- Prometheus `stock-node` scrape job
- Prometheus의 `stock-node` dependency wiring
- stock-node 전용 Grafana dashboard
- stock-node down alert
- Finnhub refresh failure/stalled alert
- quote cache empty/stale alert
- 자동 청산/거래 거절 관련 business metric
- 운영 문서의 stock-node 관측성 반영

## 3. 책임 경계

이번 작업은 관측성만 다룬다.

포함 범위:

- Prometheus scrape 설정
- Grafana dashboard provisioning
- Grafana alert provisioning
- Micrometer 기반 stock 비즈니스 metric
- 운영 문서 갱신
- 테스트와 smoke check 갱신

제외 범위:

- OpenTelemetry trace 도입
- Tempo 도입
- Grafana/Prometheus 버전 변경
- Loki pipeline 대규모 재구성
- stock 거래 로직 변경
- Finnhub provider 정책 변경
- Discord command 응답 포맷 변경

## 4. 목표 메트릭

### 4.1 기본 actuator/JVM 메트릭

Prometheus scrape가 붙으면 Spring Boot Actuator가 기본 제공하는 메트릭을 사용할 수 있다.

- `up{job="stock-node"}`
- JVM memory
- JVM threads
- HTTP server request metrics
- process CPU
- application started/ready metrics

### 4.2 stock 비즈니스 메트릭

Micrometer로 아래 메트릭을 추가한다.

```text
stock_commands_total
stock_command_duration_seconds
stock_quote_refresh_total
stock_quote_refresh_failures_total
stock_quote_refresh_success_total
stock_auto_liquidations_total
stock_trade_rejections_total
stock_trade_executions_total
stock_provider_rate_limit_exceeded_total
```

권장 label:

- `command`
- `result`
- `market`
- `symbol`
- `reason`
- `side`
- `provider`

주의:

- `guildId`, `userId`, `commandId`는 label로 넣지 않는다.
- cardinality가 큰 값은 metric label에서 제외한다.
- 상세 식별자는 로그에 남긴다.

## 5. Alert 후보

### 5.1 stock-node down

조건:

```promql
up{job="stock-node"} == 0
```

의미:

- stock worker가 Prometheus scrape에 응답하지 않는다.
- `/stock` 명령 처리, 시세 refresh, 자동 청산이 중단될 수 있다.

심각도:

- `critical`

### 5.2 Finnhub refresh stalled

조건 예시:

```promql
increase(stock_quote_refresh_success_total{provider="finnhub"}[2m]) == 0
```

의미:

- 2분 동안 Finnhub refresh 성공이 없다.
- Redis quote cache가 stale 또는 empty 상태로 갈 수 있다.

심각도:

- `warning`

### 5.3 Finnhub refresh failures high

조건 예시:

```promql
increase(stock_quote_refresh_failures_total{provider="finnhub"}[5m]) > 10
```

의미:

- Finnhub API 실패, rate limit, network 문제 가능성이 있다.

심각도:

- `warning`

### 5.4 trade rejection spike

조건 예시:

```promql
increase(stock_trade_rejections_total[5m]) > 10
```

의미:

- quote stale, 잔고 부족, 보유 수량 부족, validation 실패가 급증했다.

심각도:

- `warning`

## 6. 작업 단위

## O7-1. Prometheus scrape 연결

### 작업

- `ops/observability/prometheus/prometheus.yml`에 `stock-node` scrape job 추가
- `docker-compose.yml`의 `prometheus.depends_on`에 `stock-node` 추가
- `stock-node:8080/actuator/prometheus`를 scrape target으로 등록

### 테스트

- `docker compose --profile observability up -d prometheus stock-node`
- Prometheus target page에서 `stock-node`가 `UP`인지 확인
- `curl http://localhost:8083/actuator/prometheus` 또는 컨테이너 내부 scrape 확인

### 완료 기준

- Prometheus `targets`에서 `stock-node`가 `UP`
- `up{job="stock-node"}`가 `1`

## O7-2. stock business metric 추가

### 작업

- `stock-node-app`에 Micrometer counter/timer wrapper 추가
- command 처리 성공/실패 metric 추가
- quote refresh 성공/실패 metric 추가
- provider rate limit 초과 metric 추가
- trade execution/rejection metric 추가
- auto liquidation metric 추가

### 테스트

- metric 단위 테스트 추가
- command 처리 후 counter 증가 확인
- quote refresh 실패 시 failure counter 증가 확인
- 자동 청산 발생 시 liquidation counter 증가 확인

### 완료 기준

- `/actuator/prometheus`에서 `stock_*` metric이 보인다.
- 고카디널리티 label이 없다.

## O7-3. Grafana dashboard 추가

### 작업

- `ops/observability/grafana/dashboards`에 stock-node dashboard JSON 추가
- dashboard provisioning 설정에 stock dashboard 반영

패널 후보:

- stock-node up
- JVM heap
- quote refresh success/failure
- Finnhub refresh rate
- trade executions/rejections
- auto liquidations
- RabbitMQ stock command queue depth
- Redis quote cache 관련 지표

### 테스트

- Grafana 기동 후 dashboard 목록에서 stock dashboard 확인
- Prometheus query가 패널에서 정상 표시되는지 확인

### 완료 기준

- Grafana에서 stock-node dashboard가 열린다.
- 주요 패널이 `No data` 없이 동작한다.

## O7-4. Alert rule 추가

### 작업

- Prometheus alert rule 또는 Grafana provisioning rule에 stock-node alert 추가
- 기존 alert policy와 receiver 구조를 유지한다.
- `noDataState`는 false positive를 피하도록 신중하게 설정한다.

Alert 후보:

- `StockNodeDown`
- `StockQuoteRefreshStalled`
- `StockQuoteRefreshFailuresHigh`
- `StockTradeRejectionsHigh`

### 테스트

- rule syntax check
- Prometheus rules page 또는 Grafana alerting page에서 rule 로딩 확인
- 가능하면 임계값을 테스트용으로 낮춰 firing 조건을 수동 검증

### 완료 기준

- alert rule이 정상 로딩된다.
- Discord alert receiver를 켠 환경에서 알림 흐름이 깨지지 않는다.

## O7-5. smoke check 갱신

### 작업

- `ops/smoke-check.sh`에 stock-node health와 metrics 확인을 추가한다.
- `/actuator/health`
- `/actuator/prometheus`
- Prometheus target scrape 확인

### 테스트

- 로컬 또는 서버에서 smoke check 실행

### 완료 기준

- stock-node health와 metrics endpoint가 smoke check에 포함된다.

## O7-6. 문서 갱신

### 작업

- `docs/reference/CURRENT_ARCHITECTURE.md`
- `docs/operations/OBSERVABILITY_PLAN.md`
- `docs/operations/OPERATIONS_RUNBOOK.md`
- root `README.md`

반영 내용:

- stock-node logs는 Loki로 수집됨
- stock-node metrics는 Prometheus scrape 대상에 포함됨
- stock dashboard와 alert 목록
- 운영 확인 명령

### 테스트

- 문서 링크 확인
- 현재 코드와 다른 설명이 없는지 점검

### 완료 기준

- 더 이상 "stock-node metrics 미연결" 문구가 남아 있지 않다.
- observability 설명이 실제 compose/prometheus 설정과 일치한다.

## 7. 검증 계획

### 로컬 검증

```powershell
.\gradlew.bat :apps:stock-node-app:test
.\gradlew.bat clean test
.\gradlew.bat bootJarAll
```

### compose 검증

```bash
docker compose --profile observability up -d prometheus grafana loki alloy redis-exporter stock-node
docker compose logs stock-node --tail=100
```

확인 항목:

- `http://localhost:8083/actuator/health`
- `http://localhost:8083/actuator/prometheus`
- `http://localhost:9090/targets`
- `http://localhost:3000`

### Prometheus query

```promql
up{job="stock-node"}
stock_quote_refresh_success_total
stock_quote_refresh_failures_total
stock_trade_executions_total
stock_auto_liquidations_total
```

## 8. 완료 기준

7주차 완료 기준은 다음과 같다.

- Prometheus가 `stock-node`를 scrape한다.
- Grafana에서 stock-node dashboard를 볼 수 있다.
- stock quote refresh, trade, liquidation 관련 business metric이 노출된다.
- stock-node down과 quote refresh 이상 상황에 대한 alert rule이 있다.
- smoke check와 운영 문서가 stock-node observability를 포함한다.
- 기존 gateway/audio-node observability가 깨지지 않는다.

## 9. 구현 순서

권장 순서:

1. `O7-1` Prometheus scrape 연결
2. `O7-2` business metric 추가
3. `O7-3` Grafana dashboard 추가
4. `O7-4` alert rule 추가
5. `O7-5` smoke check 갱신
6. `O7-6` 문서 갱신

이 순서가 좋은 이유는 scrape가 먼저 붙어야 metric, dashboard, alert를 순서대로 검증할 수 있기 때문이다.
