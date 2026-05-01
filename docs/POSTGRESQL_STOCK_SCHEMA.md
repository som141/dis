# PostgreSQL 주식 스키마

## 목적

이 문서는 `stock-node-app`이 사용하는 PostgreSQL 스키마를 현재 마이그레이션 기준으로 설명한다.

기준 마이그레이션:

- `V1__init_stock_schema.sql`
- `V2__add_stock_watchlist.sql`
- `V3__add_monthly_season_and_leverage.sql`

## 현재 테이블

### stock_account

역할:

- 길드/사용자별 시즌 계좌

핵심 컬럼:

- `guild_id`
- `user_id`
- `season_key`
- `cash_balance`

제약:

- `(guild_id, user_id, season_key)` unique

비고:

- 월 시즌제 이후 한 사용자는 시즌별로 여러 계좌를 가질 수 있다.
- 과거 데이터는 `season_key='legacy'`로 backfill 된다.

### stock_position

역할:

- 계좌별 현재 보유 포지션

핵심 컬럼:

- `account_id`
- `symbol`
- `quantity`
- `average_cost`
- `leverage`
- `margin_amount`
- `notional_amount`

제약:

- `(account_id, symbol)` unique

비고:

- 현재 모델은 symbol당 한 포지션만 유지한다.
- 같은 symbol 추가 매수 시 leverage가 같아야 한다.

### trade_ledger

역할:

- 매수/매도 거래 원장

핵심 컬럼:

- `account_id`
- `symbol`
- `side`
- `quantity`
- `unit_price`
- `leverage`
- `margin_amount`
- `notional_amount`
- `occurred_at`

비고:

- 포지션 스냅샷이 아니라 append-only 거래 기록이다.

### allowance_ledger

역할:

- 계좌 지급 내역 기록

핵심 컬럼:

- `account_id`
- `amount`
- `allowance_type`
- `occurred_at`

현재 allowance type:

- `MONTHLY_SEED`

비고:

- 활성 시즌 첫 진입 시 `10,000` 지급 기록이 남는다.

### account_snapshot

역할:

- 랭킹/기준자산 계산용 스냅샷

핵심 컬럼:

- `account_id`
- `snapshot_at`
- `cash_balance`
- `portfolio_value`
- `total_equity`

### stock_watchlist

역할:

- 시세 갱신 대상 종목 목록

핵심 컬럼:

- `market`
- `symbol`
- `name`
- `rank_no`
- `source`
- `base_date`
- `enabled`

제약:

- `(market, symbol)` unique

현재 seed:

- `US` market
- source `SEED_US_MARKET_CAP`
- 시가총액 상위 10개 종목

## 시즌 모델

현재 시즌 키는 `YYYY-MM` 형식이다.

예:

- `2026-05`

시즌 기준:

- timezone: `Asia/Seoul`
- rollover: 매월 1일 00:00

## 레버리지 모델

현재 1차 구현은 isolated margin 기준이다.

- `/stock buy` 입력은 정수 주식 수량으로 해석한다.
- 포지션 규모는 `margin * leverage`
- 포지션 손익은 `margin_amount`, `notional_amount`, 현재가 기준으로 계산한다.
- 20초 시세 갱신 때 `isolatedEquity = marginAmount + unrealizedPnL` 를 다시 계산하고 `0 이하`이면 자동 청산한다.

## 운영 확인 쿼리

테이블 목록:

```sql
\dt
```

watchlist 확인:

```sql
select market, symbol, rank_no, enabled
from stock_watchlist
order by market, rank_no;
```

특정 사용자 활성 시즌 계좌:

```sql
select id, guild_id, user_id, season_key, cash_balance
from stock_account
where guild_id = :guild_id
  and user_id = :user_id
order by id desc;
```

거래 원장 최근 20개:

```sql
select account_id, symbol, side, quantity, unit_price, leverage, occurred_at
from trade_ledger
order by occurred_at desc
limit 20;
```

지급 내역 확인:

```sql
select account_id, amount, allowance_type, occurred_at
from allowance_ledger
order by occurred_at desc;
```
