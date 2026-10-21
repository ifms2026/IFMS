-- V12 created control wallets with a NULL optimistic version. Money is unchanged.
UPDATE wallets SET version=0 WHERE version IS NULL;
ALTER TABLE wallets ALTER COLUMN version SET DEFAULT 0;
ALTER TABLE wallets ALTER COLUMN version SET NOT NULL;
