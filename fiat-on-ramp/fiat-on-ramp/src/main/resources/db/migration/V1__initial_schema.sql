-- ============================================================
-- S3 Fiat On-Ramp — Initial Schema
-- ============================================================

-- Guard role operations for TestContainers compatibility
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sp_user') THEN
        CREATE ROLE sp_user WITH LOGIN PASSWORD 'sp_pass';
    END IF;
END
$$;

-- ============================================================
-- collection_orders (main aggregate)
-- ============================================================
CREATE TABLE collection_orders (
    collection_id       UUID            NOT NULL DEFAULT gen_random_uuid(),
    payment_id          UUID            NOT NULL,
    merchant_id         UUID            NOT NULL,
    amount              NUMERIC(20, 8)  NOT NULL,
    currency            VARCHAR(3)      NOT NULL,
    source_country      VARCHAR(2)      NOT NULL,
    payment_rail        VARCHAR(30)     NOT NULL,
    psp                 VARCHAR(50)     NOT NULL,
    psp_reference       VARCHAR(200)    NULL,
    status              VARCHAR(30)     NOT NULL DEFAULT 'PENDING',
    failure_reason      TEXT            NULL,
    error_code          VARCHAR(100)    NULL,
    settled_amount      NUMERIC(20, 8)  NULL,
    settled_at          TIMESTAMPTZ     NULL,
    estimated_settlement_at TIMESTAMPTZ NULL,
    expires_at          TIMESTAMPTZ     NOT NULL,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    version             BIGINT          NOT NULL DEFAULT 0,
    CONSTRAINT collection_orders_pkey PRIMARY KEY (collection_id),
    CONSTRAINT collection_orders_payment_id_unique UNIQUE (payment_id),
    CONSTRAINT collection_orders_status_check CHECK (status IN (
        'PENDING', 'PSP_INITIATED', 'PSP_AUTHORIZED', 'SETTLED',
        'FAILED', 'EXPIRED', 'REFUND_PENDING', 'REFUNDED'
    ))
);

CREATE INDEX collection_orders_merchant_id_idx
    ON collection_orders (merchant_id, created_at DESC);

CREATE INDEX collection_orders_status_idx
    ON collection_orders (status, created_at)
    WHERE status NOT IN ('SETTLED', 'FAILED', 'EXPIRED', 'REFUNDED');

CREATE INDEX collection_orders_psp_reference_idx
    ON collection_orders (psp, psp_reference)
    WHERE psp_reference IS NOT NULL;

-- ============================================================
-- psp_transactions (immutable PSP event log)
-- ============================================================
CREATE TABLE psp_transactions (
    psp_transaction_id  UUID            NOT NULL DEFAULT gen_random_uuid(),
    collection_id       UUID            NOT NULL REFERENCES collection_orders(collection_id),
    psp                 VARCHAR(50)     NOT NULL,
    psp_reference       VARCHAR(200)    NOT NULL,
    event_type          VARCHAR(50)     NOT NULL,
    status              VARCHAR(30)     NOT NULL,
    amount              NUMERIC(20, 8)  NULL,
    currency            VARCHAR(3)      NULL,
    raw_response        JSONB           NOT NULL DEFAULT '{}',
    received_at         TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT psp_transactions_pkey PRIMARY KEY (psp_transaction_id)
);

CREATE INDEX psp_transactions_collection_id_idx
    ON psp_transactions (collection_id, received_at DESC);

CREATE INDEX psp_transactions_psp_reference_idx
    ON psp_transactions (psp, psp_reference);

-- ============================================================
-- refunds
-- ============================================================
CREATE TABLE refunds (
    refund_id           UUID            NOT NULL DEFAULT gen_random_uuid(),
    collection_id       UUID            NOT NULL REFERENCES collection_orders(collection_id),
    refund_amount       NUMERIC(20, 8)  NOT NULL,
    currency            VARCHAR(3)      NOT NULL,
    reason              TEXT            NULL,
    status              VARCHAR(30)     NOT NULL DEFAULT 'PENDING',
    psp_refund_ref      VARCHAR(200)    NULL,
    failure_reason      TEXT            NULL,
    initiated_at        TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    completed_at        TIMESTAMPTZ     NULL,
    estimated_completion_at TIMESTAMPTZ NULL,
    CONSTRAINT refunds_pkey PRIMARY KEY (refund_id),
    CONSTRAINT refunds_status_check CHECK (status IN (
        'PENDING', 'PROCESSING', 'COMPLETED', 'FAILED'
    ))
);

CREATE INDEX refunds_collection_id_idx
    ON refunds (collection_id);

CREATE INDEX refunds_status_idx
    ON refunds (status, initiated_at)
    WHERE status NOT IN ('COMPLETED', 'FAILED');

-- ============================================================
-- reconciliation_records
-- ============================================================
CREATE TABLE reconciliation_records (
    reconciliation_id   UUID            NOT NULL DEFAULT gen_random_uuid(),
    collection_id       UUID            NOT NULL REFERENCES collection_orders(collection_id),
    psp                 VARCHAR(50)     NOT NULL,
    psp_reference       VARCHAR(200)    NOT NULL,
    expected_amount     NUMERIC(20, 8)  NOT NULL,
    actual_amount       NUMERIC(20, 8)  NULL,
    currency            VARCHAR(3)      NOT NULL,
    status              VARCHAR(30)     NOT NULL DEFAULT 'PENDING',
    discrepancy_type    VARCHAR(50)     NULL,
    discrepancy_amount  NUMERIC(20, 8)  NULL,
    reconciled_at       TIMESTAMPTZ     NULL,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT reconciliation_records_pkey PRIMARY KEY (reconciliation_id),
    CONSTRAINT reconciliation_records_status_check CHECK (status IN (
        'PENDING', 'MATCHED', 'DISCREPANCY', 'UNMATCHED'
    ))
);

CREATE INDEX reconciliation_records_collection_id_idx
    ON reconciliation_records (collection_id);

CREATE INDEX reconciliation_records_status_idx
    ON reconciliation_records (status, created_at)
    WHERE status NOT IN ('MATCHED');

-- ============================================================
-- Namastack outbox tables (prefix: onramp_)
-- ============================================================

-- Namastack outbox record table (event storage + relay)
CREATE TABLE onramp_outbox_record (
    id              VARCHAR(255) PRIMARY KEY,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    record_key      VARCHAR(255),
    record_type     VARCHAR(255) NOT NULL,
    payload         TEXT NOT NULL,
    context         TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ,
    next_retry_at   TIMESTAMPTZ,
    failure_count   INTEGER NOT NULL DEFAULT 0,
    failure_reason  TEXT,
    partition_no    INTEGER,
    handler_id      VARCHAR(255)
);

CREATE INDEX idx_onramp_outbox_record_status
    ON onramp_outbox_record (status, next_retry_at);

-- Namastack outbox instance tracking
CREATE TABLE onramp_outbox_instance (
    instance_id     VARCHAR(255) PRIMARY KEY,
    hostname        VARCHAR(255),
    port            INTEGER,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    started_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_heartbeat  TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Namastack outbox partition assignment
CREATE TABLE onramp_outbox_partition (
    partition_number INTEGER PRIMARY KEY,
    instance_id      VARCHAR(255),
    version          BIGINT NOT NULL DEFAULT 0,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE onramp_outbox_record IS 'Namastack outbox — reliable event publishing for fiat-on-ramp';
