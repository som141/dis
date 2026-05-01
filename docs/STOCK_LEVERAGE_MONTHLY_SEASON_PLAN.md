# Stock Leverage and Monthly Season Plan

## 1. Goal

Add leverage trading to the stock game and replace the current daily allowance model with a monthly season model.

Target behavior:

- the current `DailyAllowanceService` rule is removed
- each user receives monthly seed cash of `10,000` at the start of the month
- the stock game resets every month at `day 1 00:00`
- leverage is supported on `buy`
- ranking, portfolio, balance, history, and snapshots are scoped to the active month

## 2. Policy Freeze

The implementation plan below assumes these rules.

### 2.1 Monthly operation rule

- season unit: one calendar month
- season timezone: `Asia/Seoul`
- season key format: `YYYY-MM`
- monthly seed cash: `10,000`
- reset time: every month `01 00:00:00` KST

### 2.2 Reset rule

- active trading state is reset at season rollover
- old season data is preserved for audit and future review
- current commands operate on the active season only unless later extended

### 2.3 Leverage rule for first implementation

- `/stock buy` accepts optional `leverage`
- allowed range: `1` to `50`
- default leverage: `1`
- leverage model: isolated margin
- buy input is an integer share quantity
- position notional = `quantity * entryPrice`
- margin = `notional / leverage`
- one account may hold only one active position per symbol in the same season
- if the same symbol is bought again, leverage must match the existing position
- different leverage on an existing symbol is rejected
- `50x` leverage adds a warning message to the response

### 2.4 Realization rule

- opening a leveraged buy deducts only margin cash from account cash
- closing a position realizes PnL using current quote and position quantity
- released cash on sell = remaining margin + realized PnL
- account cash cannot go below zero from settlement

### 2.5 Scope boundary

The first leverage iteration does **not** include:

- forced liquidation engine
- maintenance margin
- partial liquidation
- borrow interest
- cross margin
- multi-lot leverage blending per symbol

## 3. Current Impacted Areas

Current code paths that must change:

- `DailyAllowanceService`
- `TradeExecutionService`
- `RankingService`
- `SnapshotService`
- `SnapshotScheduler`
- `stock_account` unique key strategy
- `stock_position` shape
- `trade_ledger` shape
- `allowance_ledger` semantics

This is a real domain migration, not a small patch.

## 4. Recommended Design Direction

### 4.1 Season-aware data model

Do not hard-delete current rows on reset.

Use a season model instead:

- add `stock_season`
- make account and trading data belong to a season
- keep history per season
- current commands resolve the active season before reading or writing

### 4.2 Why not destructive monthly truncate

A destructive reset would make these harder:

- debugging
- ranking verification
- trade audit
- future season history commands

Season-scoped rows are more work up front but safer and easier to reason about.

## 5. Work Slices

### S0. Policy lock and domain freeze

Purpose:

- freeze the leverage and monthly season rules before code changes

Work:

- confirm monthly seed amount as exact numeric `10,000`
- confirm season timezone as `Asia/Seoul`
- confirm current commands default to active season only
- confirm first leverage version is isolated margin without forced liquidation

Tests:

- none

Done when:

- rules above are accepted as the implementation target

### S1. Schema migration for monthly seasons

Purpose:

- introduce season-aware persistence without losing existing data

Work:

- add `stock_season` table
- add active season seed row creation logic
- add `season_id` or `season_key` linkage to:
  - `stock_account`
  - `stock_position`
  - `trade_ledger`
  - `allowance_ledger`
  - `account_snapshot`
- change account uniqueness from:
  - `(guild_id, user_id)`
  - to `(guild_id, user_id, season_id)`
- seed current active season for migration safety

Tests:

- Flyway migration test
- repository uniqueness test for season-scoped accounts
- repository query test for active season filtering

Done when:

- one user can have multiple monthly accounts across seasons
- current month account remains unique

### S2. Replace daily allowance with monthly seed settlement

Purpose:

- remove per-day allowance and replace it with per-season seeded cash

Work:

- replace `DailyAllowanceService` with `SeasonAccountService`
- active season account creation should seed `10,000`
- remove daily lazy settlement checks
- record seed event in `allowance_ledger` using a monthly seed type
- evict ranking cache when new season account is created

Tests:

- first command in empty season creates seeded account
- repeated command in same season does not reseed
- next season creates a new seeded account

Done when:

- no code path still grants daily cash

### S3. Monthly reset scheduler

Purpose:

- switch the system into a new season at month boundary

Work:

- add `MonthlySeasonScheduler`
- run at `0 0 0 1 * *` in `Asia/Seoul`
- create next active season row
- optionally prewarm rankings or snapshots for the new season
- evict current-season ranking cache
- add lazy season guard on command path in case scheduler was missed during downtime

Tests:

- scheduler creates next season at boundary
- command path lazy-creates active season if scheduler was missed
- ranking cache eviction test

Done when:

- season rollover is correct both with and without scheduler uptime continuity

### S4. Leverage position model

Purpose:

- store enough information to value and settle leveraged positions

Work:

- extend `stock_position` with:
  - `leverage`
  - `margin_amount`
  - `notional_amount`
- extend `trade_ledger` with:
  - `season_id`
  - `leverage`
  - `margin_amount`
  - `notional_amount`
- keep one active position per `(account_id, symbol, season_id)`
- reject leverage mismatch on additional buy for same symbol

Tests:

- save/load leveraged position
- reject duplicate symbol with different leverage
- allow same symbol add-on with same leverage

Done when:

- leveraged positions can be persisted and reloaded without ambiguity

### S5. Leverage buy/sell execution

Purpose:

- support leveraged trading while keeping current quote freshness rules

Work:

- extend `StockCommand.Buy` and gateway slash command with optional leverage
- validate leverage `1..50`
- buy:
  - validate integer share quantity
  - compute notional from quantity and quote
  - deduct margin cash
- sell:
  - use current quote
  - realize PnL
  - release margin and PnL back to cash
- add `50x` warning text
- keep existing quote freshness rule

Tests:

- leverage default is `1`
- invalid leverage is rejected
- buy with leverage opens position correctly
- sell realizes leveraged PnL correctly
- `50x` warning is appended
- stale quote still rejects trade

Done when:

- leveraged buy/sell is end-to-end correct for first-pass accounting

### S6. Query and ranking season scoping

Purpose:

- make all read models resolve the active season only

Work:

- `balance` reads active season account
- `portfolio` reads active season positions
- `history` reads active season trade ledger
- `rank` computes only for active season accounts
- `snapshot` stores season-linked snapshots
- baseline logic becomes season-aware

Tests:

- balance ignores prior season cash
- portfolio ignores prior season positions
- history ignores prior season trades
- ranking includes only active season accounts

Done when:

- old season data no longer leaks into current results

### S7. Response and command contract updates

Purpose:

- surface the new monthly and leverage rules cleanly to Discord users

Work:

- update slash command definition for optional leverage
- update stock response formatter
- add season reset and seed wording where needed
- add warning text for `50x`

Tests:

- gateway command preparation test
- formatter tests for leverage output
- formatter tests for monthly season seed wording

Done when:

- Discord command contracts match the new rules

### S8. Documentation and runbooks

Purpose:

- leave the operation model explicit

Work:

- update README
- update stock docs
- add:
  - monthly seed `10,000`
  - reset time `every month day 1 00:00 KST`
  - active season behavior
  - leverage range `1..50`
  - isolated margin first-pass limitation

Tests:

- manual doc review

Done when:

- docs match real runtime behavior

## 6. Execution Order

Recommended order:

1. `S0` policy lock
2. `S1` schema migration
3. `S2` monthly seed settlement
4. `S3` monthly reset scheduler
5. `S4` leverage position model
6. `S5` leverage buy/sell execution
7. `S6` query and ranking season scoping
8. `S7` response and command contract updates
9. `S8` documentation

Reason:

- season modeling is the foundation
- leverage is safer after account lifecycle is season-aware
- read models should move only after write paths are stable

## 7. Validation Commands

After each slice:

```powershell
.\gradlew.bat :apps:stock-node-app:test
.\gradlew.bat :apps:gateway-app:test
```

Before push:

```powershell
.\gradlew.bat clean test
.\gradlew.bat bootJarAll
```

Runtime validation after implementation:

```powershell
docker compose logs stock-node --tail=200
docker compose logs gateway --tail=200
```

Season reset spot checks:

```sql
select * from stock_season order by id desc;
select * from stock_account order by id desc;
select * from allowance_ledger order by id desc;
```

## 8. Risks

### 8.1 Account uniqueness migration

Current account uniqueness is not season-aware.

Risk:

- migration can break existing account lookups if changed carelessly

Mitigation:

- move all account queries behind a season-aware service before broad usage

### 8.2 Ranking baseline logic

Current ranking mixes allowance sums and snapshots.

Risk:

- season reset can make old baseline logic incorrect

Mitigation:

- switch ranking to active-season-only first, then simplify baseline rules

### 8.3 Leverage accounting ambiguity

Leverage without a clear isolated margin model becomes inconsistent quickly.

Risk:

- portfolio valuation and realized PnL diverge

Mitigation:

- enforce one active leverage value per symbol per season for phase 1

## 9. Success Criteria

- daily allowance path is fully removed
- monthly seed `10,000` is applied once per season
- active season resets at `day 1 00:00 KST`
- leveraged buy supports `1..50`
- current season balance, portfolio, history, and rank are correct
- `50x` warning is shown
- tests pass before deployment
