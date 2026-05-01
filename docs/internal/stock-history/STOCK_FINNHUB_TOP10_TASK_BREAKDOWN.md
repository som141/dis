# Stock Finnhub Top10 Task Breakdown

## 1. Goal

Add a cache-first US Top10 market data pipeline for the stock game.

Target behavior:

- `stock-node-app` refreshes US Top10 quotes every 20 seconds using Finnhub REST API only
- refreshed quotes are stored in Redis with TTL 60 seconds
- Discord stock commands do not call Finnhub directly
- `quote`, `buy`, `portfolio`, `rank`, and the new `list` command read quotes from Redis only
- trade execution is allowed only when quote age is within 45 seconds
- stale cached quotes may still be shown for read-only commands
- `MockQuoteProvider` remains available for tests and local fallback-free runs

Scope note:

- leverage is excluded from this task
- WebSocket support is excluded from this task
- real trading is excluded; this remains a mock investment game

## 2. Implementation Principles

- Keep existing music bot behavior untouched
- Preserve the current `gateway-app -> stock command -> stock-node-app -> result event` flow
- Separate background market-data refresh from command handling
- Make runtime provider selection explicit through configuration
- Add tests per slice and do not advance until the current slice is green

## 3. Work Slices

### S0. Preflight and config alignment

Purpose:

- align current quote/provider config with the new Finnhub naming and policy
- freeze the target runtime contract before code changes

Work:

- add `stock.quote.provider`
- add `stock.market-data.*`
- add `stock.finnhub.*`
- keep `mock` as the default provider
- map compose/env values for local runtime
- decide whether old AlphaVantage config is removed or left unused

Tests:

- configuration binding test for `StockQuoteProperties`
- configuration binding test for `FinnhubProperties`
- application context test with provider=`mock`
- application context failure or warning-path test with provider=`finnhub` and blank API key

Done when:

- the application starts with `mock`
- the application clearly rejects or warns on invalid `finnhub` setup

### S1. Watchlist persistence

Purpose:

- add a DB-backed source of truth for the US Top10 seed list

Work:

- add Flyway migration for `stock_watchlist`
- add unique constraint on `(market, symbol)`
- insert the provided US Top10 seed rows
- add entity and repository
- add `StockWatchlistService`

Tests:

- migration test verifying table creation
- repository test verifying seed count for `US`
- repository test verifying rank order
- repository test verifying unique constraint behavior

Done when:

- `stock_watchlist` exists
- `US` Top10 seeds are queryable in `rank_no` order

### S2. Finnhub client and mapping

Purpose:

- add a dedicated Finnhub REST quote client without coupling command flow to external I/O

Work:

- add `FinnhubProperties`
- add `FinnhubQuoteResponse`
- add `FinnhubClient` using `WebClient`
- add `FinnhubQuoteMapper`
- validate null and non-positive prices
- normalize symbols such as `BRK.B`

Tests:

- `FinnhubQuoteMapperTest`
- `FinnhubClientTest` with mock HTTP server
- invalid payload test
- missing API key behavior test

Done when:

- one symbol can be fetched and mapped into internal `StockQuote`
- invalid Finnhub payloads are rejected cleanly

### S3. Provider split: command path vs background refresh path

Purpose:

- stop command handling from calling any external provider directly

Work:

- refactor quote flow so command-time reads are cache-only
- keep provider-backed refresh logic in a separate market-data refresh service
- add a cache read API that returns `fresh`, `stale`, or `missing`
- update trade path to reject `missing` and stale quotes
- preserve stale read support for query/portfolio/rank/list

Tests:

- `buy` with missing quote -> rejected
- `buy` with stale quote -> rejected
- `buy` with fresh quote -> allowed
- `quote` with stale cached quote -> allowed and marked stale
- `portfolio` with stale cached quote -> allowed and marked stale
- `rank` with stale cached quote -> allowed and uses cached data only

Done when:

- no Discord stock command path triggers provider I/O
- trade freshness is enforced at 45 seconds

### S4. Top10 refresh scheduler

Purpose:

- refresh US Top10 prices in the background every 20 seconds

Work:

- add `MarketDataProperties`
- add `FinnhubTop10RefreshScheduler`
- enable only when:
  - `stock.market-data.enabled=true`
  - `stock.quote.provider=finnhub`
- load enabled watchlist items by market and rank
- fetch 10 symbols sequentially
- save to Redis with TTL 60 seconds
- log per-run success/failure summary
- continue on individual symbol failure

Tests:

- scheduler disabled when provider=`mock`
- scheduler disabled when market-data is disabled
- refresh service stores successful quotes into Redis
- refresh service continues when 1 of 10 symbols fails
- refresh service calls symbols in rank order

Done when:

- a single refresh run populates Redis keys for the watchlist
- one symbol failure does not abort the rest of the run

### S5. `/stock list` command

Purpose:

- expose the US Top10 watchlist as a Discord-visible read command

Work:

- add `List` command contract to `stock-core`
- add gateway slash subcommand and dispatch path
- add stock-node list handling
- render:
  - symbol
  - company name
  - current price
  - change rate if available
  - `quote pending` when absent
  - stale marker when cached but old
- include footer text stating:
  - data source is Finnhub REST API
  - refresh period is 20 seconds

Tests:

- gateway command preparation test
- listener routing test
- formatter test for quote present
- formatter test for quote missing
- formatter test for stale marker
- end-to-end messaging test for `/stock list`

Done when:

- `/stock list` returns the US Top10 list from watchlist + Redis cache

### S6. Portfolio and ranking cache-only hardening

Purpose:

- make sure existing read models still work after the quote-flow split

Work:

- update `PortfolioService` to use cache-only quote reads
- update ranking flow to use cache-only reads
- keep stale display semantics for read models
- verify behavior when one or more symbols are missing

Tests:

- portfolio integration test with fresh quotes
- portfolio integration test with stale quotes
- portfolio integration test with missing quote placeholder or failure policy
- rank integration test with cached quotes only

Done when:

- `balance`, `portfolio`, `history`, and `rank` still pass their current tests
- no accidental provider call remains in these paths

### S7. Compose and local runtime wiring

Purpose:

- make local execution straightforward and reproducible

Work:

- update `.env.example`
- update `docker-compose.yml` stock-node env entries
- wire `FINNHUB_API_KEY`
- ensure `mock` remains the default safe mode

Tests:

- `docker compose config` validation
- application context with compose-style env values

Done when:

- local runtime can be switched between `mock` and `finnhub` by env only

### S8. Documentation

Purpose:

- leave explicit runtime and behavior documentation

Work:

- update root `README.md`
- add:
  - `FINNHUB_API_KEY` setup
  - `STOCK_QUOTE_PROVIDER=finnhub`
  - US Top10 seed list
  - refresh interval 20 seconds
  - Redis TTL 60 seconds
  - trade freshness 45 seconds
  - Discord commands do not call external API directly
  - this is a mock investment game, not real trading

Tests:

- manual doc review against implementation

Done when:

- README reflects the real runtime behavior and setup steps

## 4. Execution Order

Recommended order:

1. `S0` config alignment
2. `S1` watchlist persistence
3. `S2` Finnhub client and mapper
4. `S3` cache-only quote flow split
5. `S4` scheduler
6. `S5` `/stock list`
7. `S6` portfolio/rank hardening
8. `S7` compose/env wiring
9. `S8` documentation

Reason:

- `S3` is the architectural pivot
- `S4` depends on `S1` and `S2`
- `S5` depends on `S1` and the cache behavior from `S3`
- `S6` is safest after cache-only semantics are finalized

## 5. Validation Commands

Target validation sequence after each meaningful slice:

```powershell
.\gradlew.bat :apps:stock-node-app:test
.\gradlew.bat :apps:gateway-app:test
```

Full regression before push:

```powershell
.\gradlew.bat clean test
.\gradlew.bat bootJarAll
```

Runtime verification after env setup:

```powershell
docker compose up -d redis rabbitmq postgres stock-node gateway
docker compose logs stock-node --tail=100
```

Redis spot checks:

```powershell
docker exec -it <redis-container> redis-cli KEYS "stock:quote:US:*"
docker exec -it <redis-container> redis-cli GET stock:quote:US:NVDA
```

## 6. Required Input Before Runtime Verification

Implementation can start without the real API key.

Runtime verification needs:

- `FINNHUB_API_KEY` in local `.env`
- optional server or GitHub Actions secret later if deployment is desired

## 7. Success Criteria

- `stock_watchlist` exists with US Top10 seeds
- Finnhub Top10 refresh runs every 20 seconds when provider=`finnhub`
- Redis stores `stock:quote:US:<SYMBOL>` keys with TTL 60 seconds
- `/stock list` shows the US Top10 list with quote/stale/pending states
- `/stock buy` rejects missing or stale quotes older than 45 seconds
- `/stock quote`, `/stock portfolio`, `/stock rank` read Redis cache only
- `mock` mode still works without any API key
