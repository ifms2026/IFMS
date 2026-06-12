-- =============================================================================
-- V12: Refactor SystemFund → CompanyFund (rename table + add reconciliation fields)
--      + Seed Wallet(COMPANY_FUND) as the authoritative balance tracker
--      + Seed Wallet(FLOAT_MAIN) as the system-wide invariant control wallet
--      + Bootstrap opening LedgerEntry for COMPANY_FUND wallet
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1. Rename system_funds → company_funds
-- -----------------------------------------------------------------------------
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'system_funds'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = 'public' AND table_name = 'company_funds'
    ) THEN
        ALTER TABLE system_funds RENAME TO company_funds;
    END IF;
END$$;


-- -----------------------------------------------------------------------------
-- 2. Add reconciliation fields to company_funds
-- -----------------------------------------------------------------------------
ALTER TABLE company_funds
    ADD COLUMN IF NOT EXISTS external_bank_balance     DECIMAL(19, 2) DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_statement_date       DATE,
    ADD COLUMN IF NOT EXISTS last_statement_updated_by BIGINT;

-- Deprecate total_balance — balance is now tracked by Wallet(COMPANY_FUND)
ALTER TABLE company_funds ALTER COLUMN total_balance DROP NOT NULL;
ALTER TABLE company_funds ALTER COLUMN total_balance SET DEFAULT 0;


-- -----------------------------------------------------------------------------
-- 3. Update wallets.owner_type check constraint to include new enum values
--    (Hibernate may have generated this constraint from the old enum at DDL time)
-- -----------------------------------------------------------------------------
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'wallets_owner_type_check' AND conrelid = 'wallets'::regclass
    ) THEN
        ALTER TABLE wallets DROP CONSTRAINT wallets_owner_type_check;
    END IF;
END$$;

ALTER TABLE wallets
    ADD CONSTRAINT wallets_owner_type_check
    CHECK (owner_type IN ('USER', 'DEPARTMENT', 'PROJECT', 'SYSTEM_FUND', 'COMPANY_FUND', 'FLOAT_MAIN'));


-- -----------------------------------------------------------------------------
-- 4. Rename SYSTEM_FUND → COMPANY_FUND in wallets (if any exist from prior seeds)
-- -----------------------------------------------------------------------------
UPDATE wallets SET owner_type = 'COMPANY_FUND' WHERE owner_type = 'SYSTEM_FUND';


-- -----------------------------------------------------------------------------
-- 5. Seed Wallet(COMPANY_FUND, ownerId=1) — balance = current total_balance
-- -----------------------------------------------------------------------------
INSERT INTO wallets (owner_type, owner_id, balance, locked_balance, created_at, updated_at)
SELECT 'COMPANY_FUND', 1, COALESCE(cf.total_balance, 0), 0, NOW(), NOW()
FROM company_funds cf
WHERE cf.id = 1
ON CONFLICT ON CONSTRAINT uk_wallet_owner DO NOTHING;


-- -----------------------------------------------------------------------------
-- 6. Bootstrap opening LedgerEntry for Wallet(COMPANY_FUND) so that
--    the ledger is the source of truth from the start.
--    Uses SYSTEM_ADJUSTMENT to record the migrated initial balance.
-- -----------------------------------------------------------------------------
DO $$
DECLARE
    v_wallet_id   BIGINT;
    v_opening_bal DECIMAL(19, 2);
    v_txn_id      BIGINT;
BEGIN
    SELECT id INTO v_wallet_id
    FROM wallets
    WHERE owner_type = 'COMPANY_FUND' AND owner_id = 1;

    SELECT COALESCE(total_balance, 0) INTO v_opening_bal
    FROM company_funds
    WHERE id = 1;

    IF v_wallet_id IS NOT NULL AND v_opening_bal > 0 THEN
        -- Only create opening entry if no prior entries exist for this wallet
        IF NOT EXISTS (SELECT 1 FROM ledger_entries WHERE wallet_id = v_wallet_id) THEN
            INSERT INTO transactions
                (transaction_code, amount, type, status, gateway_provider,
                 reference_type, reference_id, description, created_at)
            VALUES
                ('TXN-OPENING-COMPANY', v_opening_bal, 'SYSTEM_ADJUSTMENT', 'SUCCESS',
                 'INTERNAL', 'SYSTEM', 1,
                 'Opening balance — migrated from company_funds.total_balance (V12)', NOW())
            RETURNING id INTO v_txn_id;

            INSERT INTO ledger_entries
                (transaction_id, wallet_id, direction, amount, balance_after, created_at)
            VALUES
                (v_txn_id, v_wallet_id, 'CREDIT', v_opening_bal, v_opening_bal, NOW());
        END IF;
    END IF;
END$$;


-- -----------------------------------------------------------------------------
-- 7. Seed Wallet(FLOAT_MAIN, ownerId=0) — balance = SUM of all non-FLOAT_MAIN wallets
--    ownerId = 0 is a sentinel value (no real entity owns FLOAT_MAIN)
-- -----------------------------------------------------------------------------
INSERT INTO wallets (owner_type, owner_id, balance, locked_balance, created_at, updated_at)
VALUES (
    'FLOAT_MAIN',
    0,
    COALESCE((SELECT SUM(balance) FROM wallets WHERE owner_type != 'FLOAT_MAIN'), 0),
    0,
    NOW(),
    NOW()
)
ON CONFLICT ON CONSTRAINT uk_wallet_owner DO NOTHING;
