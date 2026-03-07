-- ============================================================
-- Add version columns for JPA optimistic locking
-- ============================================================

ALTER TABLE compliance_checks
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE customer_risk_profiles
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
