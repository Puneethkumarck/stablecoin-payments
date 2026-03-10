-- ============================================================
-- S5 Fiat Off-Ramp — Add bank account and mobile money columns
-- ============================================================
-- PayoutOrder domain model has BankAccount and MobileMoneyAccount
-- value objects that need to be flattened into the payout_orders table.
-- At least one of bank or mobile money must be set (application-level invariant).

-- BankAccount VO columns
ALTER TABLE payout_orders ADD COLUMN bank_account_number VARCHAR(50);
ALTER TABLE payout_orders ADD COLUMN bank_code           VARCHAR(50);
ALTER TABLE payout_orders ADD COLUMN bank_account_type   VARCHAR(20);
ALTER TABLE payout_orders ADD COLUMN bank_country        VARCHAR(2);

-- MobileMoneyAccount VO columns
ALTER TABLE payout_orders ADD COLUMN mobile_money_provider VARCHAR(30);
ALTER TABLE payout_orders ADD COLUMN mobile_money_phone    VARCHAR(30);
ALTER TABLE payout_orders ADD COLUMN mobile_money_country  VARCHAR(2);

-- ============================================================
-- Flyway role guard for TestContainers compatibility
-- ============================================================
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sp_readonly') THEN
        GRANT SELECT ON ALL TABLES IN SCHEMA public TO sp_readonly;
    END IF;
END $$;
