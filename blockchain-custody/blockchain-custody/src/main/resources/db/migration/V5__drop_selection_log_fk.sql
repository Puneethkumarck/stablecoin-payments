-- ============================================================
-- Drop FK on chain_selection_log.transfer_id
--
-- The chain selection log is written BEFORE the chain_transfer
-- row exists (selection happens first, then the transfer is
-- created). The FK constraint prevents this flow.
-- ============================================================
ALTER TABLE chain_selection_log
    DROP CONSTRAINT IF EXISTS chain_selection_log_transfer_id_fkey;
