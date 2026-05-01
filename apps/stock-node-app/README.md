# stock-node-app

## 역할

`stock-node-app`은 주식 worker다. Discord/JDA는 직접 다루지 않고, RabbitMQ command를 consume해 quote 조회, 매매, 잔고/포트폴리오/랭킹 계산을 수행한다.

## 주요 책임

- stock command consumer
- Redis quote cache 조회
- Finnhub 기반 quote refresh
- PostgreSQL persistence
- 월 시즌 계좌 관리
- 레버리지 매수/매도 처리
- 랭킹/스냅샷 계산
- result event publish

## 현재 시세 정책

- provider: `mock` 또는 `finnhub`
- watchlist: 미국 시가총액 상위 10개
- refresh 주기: 20초
- Redis TTL: 60초
- 거래 freshness: 45초
- Discord 명령 처리 중 외부 API 직접 호출 금지

## 주요 저장소

- Redis
  - quote cache
  - ranking cache
- PostgreSQL
  - account
  - position
  - trade ledger
  - allowance ledger
  - snapshot
  - watchlist

자세한 스키마는 [docs/POSTGRESQL_STOCK_SCHEMA.md](../../docs/POSTGRESQL_STOCK_SCHEMA.md)를 본다.

## health endpoint

- `/actuator/health`
- `/actuator/info`
- `/actuator/prometheus`
