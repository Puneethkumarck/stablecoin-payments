-- ============================================================
-- V4: Fix NUMERIC(20,0) → BIGINT for Long-mapped columns
-- Hibernate maps Java Long to BIGINT, not NUMERIC.
-- ============================================================

ALTER TABLE chain_transfers ALTER COLUMN block_number TYPE BIGINT;
ALTER TABLE chain_transfers ALTER COLUMN nonce TYPE BIGINT;

ALTER TABLE wallet_balances ALTER COLUMN last_indexed_block TYPE BIGINT;

ALTER TABLE wallet_nonces ALTER COLUMN current_nonce TYPE BIGINT;
