# Stock Leverage and Monthly Season Report

## 1. Goal

This change set replaces the old daily allowance model with a monthly season model and adds first-pass leverage support to stock buy/sell flows.

Applied rules:

- monthly seed cash: `10,000`
- active season unit: calendar month
- season timezone: `Asia/Seoul`
- season rollover point: day `1` at `00:00`
- leverage range: `1..50`
- leverage model: isolated margin

## 2. Implemented Changes

### 2.1 Season-aware accounts

`stock_account` is now season-scoped by `season_key`.

Changes:

- `stock_account.season_key` added
- uniqueness changed from `(guild_id, user_id)` to `(guild_id, user_id, season_key)`
- legacy rows are backfilled to `season_key='legacy'`
- active commands resolve the current season key from `Asia/Seoul`

Main files:

- `apps/stock-node-app/src/main/resources/db/migration/V3__add_monthly_season_and_leverage.sql`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/StockSeasonService.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/persistence/entity/StockAccountEntity.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/persistence/repository/StockAccountRepository.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/StockAccountApplicationService.java`

### 2.2 Monthly seed instead of daily allowance

The old daily `10,000` grant path was replaced by monthly seed settlement.

Current behavior:

- first active-season command creates or resolves the season account
- if no seed ledger exists for that season account, `10,000` is credited
- repeated commands in the same season do not reseed

Main files:

- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/DailyAllowanceService.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/AllowanceType.java`

### 2.3 Monthly boundary scheduler

A season boundary scheduler was added for the configured monthly rollover point.

Current role:

- logs season rollover at `0 0 0 1 * *` in `Asia/Seoul`
- command path still remains the safety net because season resolution is time-based

Main file:

- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/MonthlySeasonScheduler.java`

### 2.4 Leverage persistence model

Leverage fields were added to positions and trade ledger rows.

Added fields:

- `stock_position.leverage`
- `stock_position.margin_amount`
- `stock_position.notional_amount`
- `trade_ledger.leverage`
- `trade_ledger.margin_amount`
- `trade_ledger.notional_amount`

Behavior:

- same symbol in the same season reuses one position
- additional buy on the same symbol must use the same leverage
- old rows are backfilled as `1x`

Main files:

- `apps/stock-node-app/src/main/java/discordgateway/stocknode/persistence/entity/StockPositionEntity.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/persistence/entity/TradeLedgerEntity.java`

### 2.5 Leverage buy/sell execution

`buy` now accepts leverage and treats `amount` as margin cash.

Current buy behavior:

- validates leverage `1..50`
- computes notional = `margin * leverage`
- computes executed quantity from quote
- deducts margin cash from account
- stores leverage/margin/notional in position and ledger

Current sell behavior:

- enforces the same quote freshness rule
- releases proportional margin
- realizes PnL using current quote
- clamps isolated position settlement to non-negative cash release

Main files:

- `modules/stock-core/src/main/java/discordgateway/stock/command/StockCommand.java`
- `apps/gateway-app/src/main/java/discordgateway/gateway/application/StockApplicationService.java`
- `apps/gateway-app/src/main/java/discordgateway/gateway/presentation/discord/DiscordBotListener.java`
- `apps/gateway-app/src/main/java/discordgateway/gateway/presentation/discord/DiscordCommandCatalog.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/TradeExecutionService.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/TradeExecutionResult.java`

### 2.6 Season-scoped ranking and snapshots

Ranking and snapshot collection were moved onto active-season account sets.

Changes:

- ranking cache keys now include `season_key`
- ranking reads active-season accounts only
- snapshots collect active-season accounts only
- ranking day/week windows now use `Asia/Seoul`

Main files:

- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/RankingService.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/RankingPeriod.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/SnapshotService.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/SnapshotScheduler.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/cache/RankingCacheRepository.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/cache/RedisRankingCacheRepository.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/cache/StockRedisKeyFactory.java`

### 2.7 Read model updates

Portfolio and trade history now expose leverage-specific information.

Changes:

- portfolio entries include leverage, margin, and notional
- trade history entries include leverage, margin, and notional
- trade response includes leverage, margin, notional, and 50x warning

Main files:

- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/PortfolioService.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/PortfolioPositionView.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/TradeHistoryEntryView.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/StockResponseFormatter.java`

## 3. Validation

Completed:

- `.\gradlew.bat compileJava compileTestJava`
- `.\gradlew.bat :apps:gateway-app:test`
- `.\gradlew.bat bootJarAll`

Current limitation:

- `.\gradlew.bat :apps:stock-node-app:test` is blocked in this environment by missing Docker/Testcontainers runtime
- the remaining failures are integration test bootstrap failures, not current Java compile or unit-test contract failures

## 4. Remaining Gaps

The following are intentionally still limited or simplified:

- no forced liquidation engine yet
- no maintenance margin model yet
- no cross-margin model
- monthly scheduler currently logs the boundary and relies on time-based active season resolution
- README was updated for the new rules, but deeper stock-specific docs may still need expansion

## 5. Conclusion

The stock system is now moving away from daily stipend semantics and toward a monthly season model with leverage-aware trading.

From this point, the next reasonable work is:

1. add more focused leverage unit tests
2. verify Testcontainers integration tests in a Docker-enabled environment
3. decide whether to expose season information explicitly in Discord responses
