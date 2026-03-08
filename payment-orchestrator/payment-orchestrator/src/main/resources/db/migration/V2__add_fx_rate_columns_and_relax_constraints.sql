-- ============================================================
-- V2: Add FX rate detail columns and relax NOT NULL constraints
-- for columns not yet populated by the domain model.
-- ============================================================

-- FX rate detail columns (flattened from FxRate value object)
ALTER TABLE payments ADD COLUMN IF NOT EXISTS fx_rate_locked_at  TIMESTAMPTZ NULL;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS fx_rate_expires_at TIMESTAMPTZ NULL;
ALTER TABLE payments ADD COLUMN IF NOT EXISTS fx_rate_provider   VARCHAR(100) NULL;

-- Relax NOT NULL on columns not yet in the domain model
ALTER TABLE payments ALTER COLUMN sender_account_id DROP NOT NULL;
ALTER TABLE payments ALTER COLUMN recipient_account_id DROP NOT NULL;
ALTER TABLE payments ALTER COLUMN purpose_code DROP NOT NULL;
