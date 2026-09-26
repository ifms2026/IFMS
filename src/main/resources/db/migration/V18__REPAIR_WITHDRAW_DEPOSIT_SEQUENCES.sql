-- Repair withdrawal and deposit code sequences for legacy schemas.
-- Existing rows are preserved and the next values continue after stored codes.

CREATE SEQUENCE IF NOT EXISTS seq_withdraw_code
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 10;

CREATE SEQUENCE IF NOT EXISTS seq_deposit_code
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 10;

SELECT setval(
    'seq_withdraw_code',
    COALESCE(
        (SELECT MAX(CAST(substring(withdraw_code FROM '[0-9]+$') AS BIGINT))
         FROM withdrawal_requests
         WHERE withdraw_code ~ '[0-9]+$'),
        1
    ),
    EXISTS (SELECT 1 FROM withdrawal_requests WHERE withdraw_code ~ '[0-9]+$')
);

SELECT setval(
    'seq_deposit_code',
    COALESCE(
        (SELECT MAX(CAST(substring(deposit_code FROM '[0-9]+$') AS BIGINT))
         FROM deposit_logs
         WHERE deposit_code ~ '[0-9]+$'),
        1
    ),
    EXISTS (SELECT 1 FROM deposit_logs WHERE deposit_code ~ '[0-9]+$')
);