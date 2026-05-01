ALTER TABLE stock_account
    ADD COLUMN season_key VARCHAR(16);

UPDATE stock_account
SET season_key = 'legacy'
WHERE season_key IS NULL;

ALTER TABLE stock_account
    ALTER COLUMN season_key SET NOT NULL;

ALTER TABLE stock_account
    DROP CONSTRAINT uk_stock_account_guild_user;

ALTER TABLE stock_account
    ADD CONSTRAINT uk_stock_account_guild_user_season UNIQUE (guild_id, user_id, season_key);

ALTER TABLE stock_position
    ADD COLUMN leverage INTEGER NOT NULL DEFAULT 1;

ALTER TABLE stock_position
    ADD COLUMN margin_amount NUMERIC(19, 4) NOT NULL DEFAULT 0;

ALTER TABLE stock_position
    ADD COLUMN notional_amount NUMERIC(19, 4) NOT NULL DEFAULT 0;

UPDATE stock_position
SET margin_amount = ROUND(quantity * average_cost, 4),
    notional_amount = ROUND(quantity * average_cost, 4)
WHERE margin_amount = 0
  AND notional_amount = 0;

ALTER TABLE trade_ledger
    ADD COLUMN leverage INTEGER NOT NULL DEFAULT 1;

ALTER TABLE trade_ledger
    ADD COLUMN margin_amount NUMERIC(19, 4) NOT NULL DEFAULT 0;

ALTER TABLE trade_ledger
    ADD COLUMN notional_amount NUMERIC(19, 4) NOT NULL DEFAULT 0;

UPDATE trade_ledger
SET margin_amount = ROUND(quantity * unit_price, 4),
    notional_amount = ROUND(quantity * unit_price, 4)
WHERE margin_amount = 0
  AND notional_amount = 0;
