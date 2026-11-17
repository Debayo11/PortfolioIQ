-- Previous close lets us show daily change without a second price lookup.
ALTER TABLE market_prices ADD COLUMN previous_close NUMERIC(24,8);

-- Asset transfers between portfolios: two linked legs share one transfer_group.
ALTER TABLE transactions ADD COLUMN transfer_group UUID;
CREATE INDEX idx_tx_transfer_group ON transactions(transfer_group) WHERE transfer_group IS NOT NULL;

-- Replace the type CHECK without relying on its auto-generated name.
DO $$
DECLARE c RECORD;
BEGIN
  FOR c IN SELECT conname FROM pg_constraint
           WHERE conrelid = 'transactions'::regclass AND contype = 'c'
             AND pg_get_constraintdef(oid) LIKE '%BUY%'
  LOOP
    EXECUTE format('ALTER TABLE transactions DROP CONSTRAINT %I', c.conname);
  END LOOP;
END $$;

ALTER TABLE transactions ADD CONSTRAINT ck_transactions_type
  CHECK (type IN ('BUY','SELL','DIVIDEND','DEPOSIT','WITHDRAWAL','FEE','TRANSFER_IN','TRANSFER_OUT'));

ALTER TABLE transactions ADD CONSTRAINT ck_transfer_has_group
  CHECK ((type IN ('TRANSFER_IN','TRANSFER_OUT')) = (transfer_group IS NOT NULL));
