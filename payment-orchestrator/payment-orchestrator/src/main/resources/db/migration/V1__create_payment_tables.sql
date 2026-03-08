-- ============================================================
-- S1 Payment Orchestrator — Initial Schema
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
-- payments (main aggregate)
-- ============================================================
CREATE TABLE payments (
    payment_id          UUID            NOT NULL DEFAULT gen_random_uuid(),
    idempotency_key     VARCHAR(128)    NOT NULL,
    correlation_id      UUID            NOT NULL,
    state               VARCHAR(50)     NOT NULL DEFAULT 'INITIATED',
    sender_id           UUID            NOT NULL,
    sender_account_id   UUID            NOT NULL,
    recipient_id        UUID            NOT NULL,
    recipient_account_id UUID           NOT NULL,
    source_amount       NUMERIC(20, 8)  NOT NULL CHECK (source_amount > 0),
    source_currency     VARCHAR(3)      NOT NULL,
    target_currency     VARCHAR(3)      NOT NULL,
    target_amount       NUMERIC(20, 8)  NULL,
    fx_quote_id         UUID            NULL,
    locked_fx_rate      NUMERIC(20, 10) NULL,
    source_country      VARCHAR(2)      NOT NULL,
    target_country      VARCHAR(2)      NOT NULL,
    chain_id            VARCHAR(20)     NULL,
    tx_hash             VARCHAR(128)    NULL,
    purpose_code        VARCHAR(50)     NOT NULL,
    reference           VARCHAR(128)    NULL,
    metadata            JSONB           NOT NULL DEFAULT '{}',
    error_code          VARCHAR(100)    NULL,
    error_message       TEXT            NULL,
    version             INT             NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    expires_at          TIMESTAMPTZ     NOT NULL,
    completed_at        TIMESTAMPTZ     NULL,
    CONSTRAINT payments_pkey PRIMARY KEY (payment_id),
    CONSTRAINT payments_state_check CHECK (state IN (
        'INITIATED','COMPLIANCE_CHECK','FX_LOCKED','FIAT_COLLECTION_PENDING',
        'FIAT_COLLECTED','ON_CHAIN_SUBMITTED','ON_CHAIN_CONFIRMED',
        'OFF_RAMP_INITIATED','SETTLED','COMPLETED','FAILED','CANCELLED',
        'COMPENSATING_FIAT_REFUND','COMPENSATING_STABLECOIN_RETURN'
    ))
);

CREATE UNIQUE INDEX payments_idempotency_key_idx
    ON payments (idempotency_key);

CREATE INDEX payments_sender_id_state_idx
    ON payments (sender_id, state, created_at DESC);

CREATE INDEX payments_state_created_at_idx
    ON payments (state, created_at DESC)
    WHERE state NOT IN ('COMPLETED', 'FAILED', 'CANCELLED');

CREATE INDEX payments_correlation_id_idx
    ON payments (correlation_id);

CREATE INDEX payments_expires_at_idx
    ON payments (expires_at)
    WHERE state NOT IN ('COMPLETED', 'FAILED', 'CANCELLED', 'COMPENSATING_FIAT_REFUND');

-- ============================================================
-- payment_audit_log (immutable state transitions)
-- ============================================================
CREATE TABLE payment_audit_log (
    log_id          UUID            NOT NULL DEFAULT gen_random_uuid(),
    payment_id      UUID            NOT NULL,
    from_state      VARCHAR(50)     NULL,
    to_state        VARCHAR(50)     NOT NULL,
    triggered_by    VARCHAR(100)    NOT NULL,
    actor           VARCHAR(100)    NULL,
    metadata        JSONB           NOT NULL DEFAULT '{}',
    occurred_at     TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT payment_audit_log_pkey PRIMARY KEY (log_id)
);

CREATE INDEX payment_audit_log_payment_id_idx
    ON payment_audit_log (payment_id, occurred_at DESC);

-- ============================================================
-- Namastack outbox tables (orchestrator_ prefix)
-- ============================================================
CREATE TABLE orchestrator_outbox_record (
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

CREATE INDEX idx_orchestrator_outbox_record_status
    ON orchestrator_outbox_record (status, next_retry_at);

CREATE TABLE orchestrator_outbox_instance (
    instance_id     VARCHAR(255) PRIMARY KEY,
    hostname        VARCHAR(255),
    port            INTEGER,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    started_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_heartbeat  TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE orchestrator_outbox_partition (
    partition_number INTEGER PRIMARY KEY,
    instance_id      VARCHAR(255),
    version          BIGINT NOT NULL DEFAULT 0,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE orchestrator_outbox_record IS 'Namastack outbox — reliable event publishing for payment-orchestrator';
