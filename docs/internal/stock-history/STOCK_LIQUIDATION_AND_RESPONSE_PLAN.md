# Stock Liquidation And Response Plan

## 1. Current State

### 1.1 Liquidation

Current code does **not** implement automatic liquidation.

What exists today:

- leverage validation `1..50`
- isolated-margin style accounting
- quote freshness check on buy/sell
- `50x` warning text in trade response

What does not exist:

- liquidation threshold calculation
- periodic liquidation scan
- forced sell / forced close event
- liquidation ledger reason
- liquidation-specific Discord message

Relevant files:

- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/TradeExecutionService.java`
- `apps/stock-node-app/src/main/java/discordgateway/stocknode/application/StockResponseFormatter.java`

### 1.2 Trade Response

Current buy/sell response exposes too much detail for ordinary users.

Current issues:

- quantity always prints with 8 decimal places
- cash values always print with 4 decimal places
- buy response shows:
  - market
  - margin
  - notional
  - remaining cash
  - remaining position quantity
- sell response shows:
  - market
  - margin
  - notional
  - remaining cash
  - remaining position quantity

The response is technically correct but too noisy for Discord usage.

## 2. Policy To Freeze Before Implementation

### 2.1 Liquidation Rule

Use a computed liquidation condition, not a fixed percentage.

Target rule:

- every 20-second refresh cycle uses the latest cached quote
- for each open leveraged position, compute current isolated equity
- if isolated equity is exhausted, liquidate immediately

Recommended formula for the current long-only model:

- `entryNotional = averageCost * quantity`
- `currentNotional = currentPrice * quantity`
- `unrealizedPnL = (currentPrice - averageCost) * quantity`
- `isolatedEquity = marginAmount + unrealizedPnL`
- liquidate when `isolatedEquity <= 0`

Equivalent long-side liquidation threshold:

- `currentPrice <= averageCost - (marginAmount / quantity)`

Because the current model uses `margin = notional / leverage`, this naturally produces different liquidation thresholds by leverage.

Examples:

- `1x` -> very low liquidation risk
- `10x` -> around 10% adverse move
- `50x` -> around 2% adverse move

### 2.2 Liquidation Price Direction

Need to define the adverse direction explicitly.

For the current long-only model:

- lower price is adverse
- if current price reaches the computed liquidation threshold
- the position is liquidated

There is no short selling model today, so only long adverse movement matters.

### 2.3 Liquidation Settlement Rule

Need to define what happens to account cash on forced close.

Recommended rule:

- use current cached quote price
- compute realized PnL the same way as manual sell
- settle account cash with:
  - `released margin + realized PnL`
  - clamp at `0`
- remove position if fully closed
- append `SELL` ledger row with a liquidation reason

### 2.4 Response Simplification Rule

Requested response simplification:

- buy response removes:
  - market
  - margin
  - notional
  - remaining cash
  - remaining position quantity
- sell response removes:
  - market
  - margin
  - notional
  - remaining cash
  - remaining position quantity

Keep:

- order user
- symbol
- leverage
- unit price
- executed quantity
- settled amount
- requested quantity
- warning text if needed

### 2.5 Decimal Display Rule

Need a more readable Discord display format.

Recommended rule:

- stock quantity:
  - strip trailing zeros
  - show up to 4 decimal places
- money:
  - show up to 2 decimal places for displayed values
  - keep internal DB precision unchanged

Examples:

- `3.00000000` -> `3`
- `1.50000000` -> `1.5`
- `59.8710` -> `59.87`

## 3. Work Breakdown

### L0. Rule Freeze

Purpose:

- freeze the exact liquidation and display rules

Work:

- confirm liquidation is computed from isolated equity, not fixed percentage
- confirm liquidation runs during every 20-second refresh
- confirm liquidation uses the latest cached refresh quote
- confirm response field removals
- confirm display precision policy

Tests:

- none

Done when:

- liquidation and formatter behavior are no longer ambiguous

### L1. Liquidation Domain Model

Purpose:

- add a clear internal path for forced liquidation

Work:

- add liquidation-specific application method
- avoid duplicating sell accounting logic
- introduce liquidation reason or execution source marker
- decide whether trade ledger stores:
  - `side='SELL'` only
  - or extra metadata for forced liquidation

Tests:

- unit test for liquidation settlement formula

Done when:

- forced close can reuse existing sell accounting without copy-paste drift

### L2. 20-Second Liquidation Scan

Purpose:

- close risky positions automatically during quote refresh

Work:

- after each successful quote refresh, find open positions for that symbol
- compute liquidation condition from `marginAmount`, `quantity`, `averageCost`, and current quote
- liquidate matching positions inside transaction
- log liquidation with account id, symbol, leverage, average cost, quote price, and computed equity
- keep one failing liquidation from blocking the rest of the refresh batch

Tests:

- one symbol refresh triggers liquidation when computed equity is zero or below
- non-matching positions remain open
- one liquidation failure does not stop remaining refresh loop

Done when:

- open risky positions are closed automatically during scheduled refresh

### L3. Liquidation Threshold Tests

Purpose:

- verify exact liquidation calculation behavior

Work:

- test isolated equity above zero: no liquidation
- test isolated equity exactly zero: liquidation
- test isolated equity below zero: liquidation
- test multiple accounts holding same symbol with different leverage
- test threshold derived from `marginAmount / quantity`

Tests:

- `LiquidationServiceTest`
- `FinnhubTop10RefreshSchedulerTest` or dedicated integration test

Done when:

- liquidation calculation semantics are explicit and stable

### L4. Ledger / Persistence Validation

Purpose:

- ensure forced liquidation leaves consistent data

Work:

- verify `stock_account` cash update
- verify `stock_position` quantity removal
- verify `trade_ledger` append
- verify ranking cache eviction
- verify snapshot/ranking reads remain consistent after liquidation

Tests:

- persistence integration test
- ranking regression test

Done when:

- no stale open position remains after liquidation

### L5. Trade Response Simplification

Purpose:

- reduce Discord noise in buy/sell output

Work:

- update `StockResponseFormatter.formatTrade`
- remove requested fields from buy/sell response
- preserve useful warning text
- keep manual and automatic sell wording distinct if needed later

Tests:

- formatter test for buy message
- formatter test for sell message

Done when:

- Discord trade message is short and readable

### L6. Decimal Formatting Cleanup

Purpose:

- make quantity and money display readable

Work:

- add display-only formatting helpers
- strip trailing zeros for quantities
- reduce displayed money precision
- keep DB precision untouched

Tests:

- quantity formatting test
- money formatting test
- regression test for quote/list/portfolio/history output

Done when:

- users no longer see long trailing zero tails in trade messages

### L7. Runtime / E2E Verification

Purpose:

- confirm the full path works in realistic execution

Work:

- simulate a leveraged buy
- inject a quote below liquidation threshold
- run scheduler path
- verify:
  - position closed
  - ledger written
  - Discord-facing result format still correct on later queries

Tests:

- stock-node integration test

Done when:

- forced liquidation works end-to-end

### L8. Documentation

Purpose:

- keep docs aligned with actual behavior

Work:

- update README
- update leverage report/runbook
- document:
  - current liquidation rule
  - refresh-driven liquidation timing
  - simplified trade response fields
  - display precision policy

Tests:

- manual doc review

Done when:

- docs match runtime behavior

## 4. Recommended Execution Order

1. `L0` rule freeze
2. `L1` liquidation domain path
3. `L2` scheduler-driven liquidation
4. `L3` threshold tests
5. `L4` persistence validation
6. `L5` response simplification
7. `L6` decimal formatting cleanup
8. `L7` runtime verification
9. `L8` documentation

## 5. Risks

### 5.1 Over-liquidation

If the threshold or direction is wrong, valid positions may be closed.

Mitigation:

- freeze the formula first
- add exact equity boundary tests

### 5.2 Quote timing ambiguity

Liquidation depends on the refresh quote, not a continuous market stream.

Mitigation:

- document clearly that liquidation is refresh-driven
- keep the check deterministic and scheduler-based

### 5.3 Formatter regression

Shortening trade output can accidentally remove important debugging information.

Mitigation:

- keep verbose data in DB and logs
- simplify only Discord-facing text

## 6. Success Criteria

- automatic liquidation exists and runs during refresh
- a long position is force-closed when computed isolated equity is exhausted
- liquidation writes consistent cash/position/ledger state
- buy/sell Discord response is shorter
- long trailing zero output is reduced
- tests pass before deployment
