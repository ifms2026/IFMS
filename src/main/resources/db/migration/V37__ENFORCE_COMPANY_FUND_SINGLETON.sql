-- CompanyFund is a singleton (id = 1) by convention across every runtime read path
-- (findDefault(), getWallet(COMPANY_FUND, 1L), etc.). Enforce it at the DB level so a
-- reseed/reset that bypasses the careful native-insert seeding path can't silently
-- produce a row with a different id and break the entire CFO fund surface.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_company_funds_singleton'
    ) THEN
        ALTER TABLE company_funds
            ADD CONSTRAINT chk_company_funds_singleton CHECK (id = 1);
    END IF;
END $$;
