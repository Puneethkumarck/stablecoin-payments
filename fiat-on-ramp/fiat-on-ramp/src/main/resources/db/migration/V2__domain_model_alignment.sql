-- ============================================================
-- S3 Fiat On-Ramp — Domain Model Alignment
-- Aligns V1 schema with finalized domain model.
-- ============================================================

-- ============================================================
-- collection_orders: status enum alignment
-- ============================================================
ALTER TABLE collection_orders
    DROP CONSTRAINT collection_orders_status_check;

ALTER TABLE collection_orders
    ADD CONSTRAINT collection_orders_status_check CHECK (status IN (
        'PENDING', 'PAYMENT_INITIATED', 'AWAITING_CONFIRMATION', 'COLLECTED',
        'COLLECTION_FAILED', 'AMOUNT_MISMATCH', 'MANUAL_REVIEW',
        'REFUND_INITIATED', 'REFUND_PROCESSING', 'REFUNDED'
    ));

-- ============================================================
-- collection_orders: add correlation_id
-- ============================================================
ALTER TABLE collection_orders
    ADD COLUMN correlation_id UUID NOT NULL DEFAULT gen_random_uuid();

-- ============================================================
-- collection_orders: add bank account columns
-- ============================================================
ALTER TABLE collection_orders
    ADD COLUMN sender_account_hash VARCHAR(128),
    ADD COLUMN sender_bank_code VARCHAR(50),
    ADD COLUMN sender_account_type VARCHAR(30),
    ADD COLUMN sender_country VARCHAR(2);

-- ============================================================
-- collection_orders: add psp_id
-- ============================================================
ALTER TABLE collection_orders
    ADD COLUMN psp_id VARCHAR(50);

-- ============================================================
-- refunds: add payment_id
-- ============================================================
ALTER TABLE refunds
    ADD COLUMN payment_id UUID;

-- ============================================================
-- psp_transactions: add direction column
-- ============================================================
ALTER TABLE psp_transactions
    ADD COLUMN direction VARCHAR(20);

-- ============================================================
-- Drop stale partial index that references old status values
-- ============================================================
DROP INDEX IF EXISTS collection_orders_status_idx;

CREATE INDEX collection_orders_status_idx
    ON collection_orders (status, created_at)
    WHERE status NOT IN ('COLLECTED', 'COLLECTION_FAILED', 'REFUNDED', 'MANUAL_REVIEW');
