-- ============================================================
-- V3: Fix version column type — INT -> BIGINT
-- Hibernate maps Long to BIGINT; schema had INT causing
-- validation mismatch.
-- ============================================================

ALTER TABLE payments ALTER COLUMN version TYPE BIGINT;
