-- ============================================================
-- S4 Blockchain & Custody — Fix chain_id nullable for PENDING transfers
-- ChainTransfer starts in PENDING state with chain_id = null;
-- chain_id is set during selectChain() transition to CHAIN_SELECTED.
-- ============================================================

ALTER TABLE chain_transfers ALTER COLUMN chain_id DROP NOT NULL;
