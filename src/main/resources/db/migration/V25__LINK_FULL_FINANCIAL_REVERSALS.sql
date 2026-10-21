ALTER TABLE transactions ADD COLUMN IF NOT EXISTS original_transaction_id BIGINT REFERENCES transactions(id);
CREATE UNIQUE INDEX IF NOT EXISTS uk_transaction_full_reversal ON transactions(original_transaction_id)
    WHERE original_transaction_id IS NOT NULL;
