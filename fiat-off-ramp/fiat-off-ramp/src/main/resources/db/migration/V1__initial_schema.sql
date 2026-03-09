-- ============================================================
-- S5 Fiat Off-Ramp — Initial Schema
-- ============================================================

-- ============================================================
-- payout_orders
-- ============================================================
CREATE TABLE payout_orders (
    payout_id               UUID            NOT NULL DEFAULT gen_random_uuid(),
    payment_id              UUID            NOT NULL,
    correlation_id          UUID            NOT NULL,
    transfer_id             UUID            NOT NULL,
    payout_type             VARCHAR(20)     NOT NULL DEFAULT 'FIAT',
    stablecoin              VARCHAR(20)     NOT NULL,
    redeemed_amount         NUMERIC(30, 8)  NOT NULL CHECK (redeemed_amount > 0),
    target_currency         VARCHAR(3)      NOT NULL,
    fiat_amount             NUMERIC(20, 8)  NULL,
    applied_fx_rate         NUMERIC(20, 10) NOT NULL,
    recipient_id            UUID            NOT NULL,
    recipient_account_hash  VARCHAR(64)     NOT NULL,
    payment_rail            VARCHAR(30)     NOT NULL,
    off_ramp_partner_id     VARCHAR(50)     NOT NULL,
    off_ramp_partner_name   VARCHAR(100)    NOT NULL,
    status                  VARCHAR(25)     NOT NULL DEFAULT 'PENDING',
    partner_reference       VARCHAR(200)    NULL,
    partner_settled_at      TIMESTAMPTZ     NULL,
    failure_reason          TEXT            NULL,
    error_code              VARCHAR(100)    NULL,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    version                 BIGINT          NOT NULL DEFAULT 0,
    CONSTRAINT payout_orders_pkey PRIMARY KEY (payout_id),
    CONSTRAINT payout_orders_payment_id_unique UNIQUE (payment_id),
    CONSTRAINT payout_orders_status_check CHECK (status IN (
        'PENDING','REDEEMING','REDEEMED','REDEMPTION_FAILED',
        'PAYOUT_INITIATED','PAYOUT_PROCESSING','COMPLETED',
        'PAYOUT_FAILED','MANUAL_REVIEW','STABLECOIN_HELD'
    )),
    CONSTRAINT payout_orders_type_check CHECK (payout_type IN ('FIAT','HOLD_STABLECOIN'))
);

CREATE INDEX payout_orders_payment_id_idx ON payout_orders (payment_id);
CREATE INDEX payout_orders_recipient_id_idx ON payout_orders (recipient_id, created_at DESC);
CREATE INDEX payout_orders_status_idx
    ON payout_orders (status, created_at)
    WHERE status IN ('REDEEMING','PAYOUT_INITIATED','PAYOUT_PROCESSING');
CREATE INDEX payout_orders_partner_ref_idx
    ON payout_orders (off_ramp_partner_id, partner_reference)
    WHERE partner_reference IS NOT NULL;

-- ============================================================
-- stablecoin_redemptions
-- ============================================================
CREATE TABLE stablecoin_redemptions (
    redemption_id       UUID            NOT NULL DEFAULT gen_random_uuid(),
    payout_id           UUID            NOT NULL REFERENCES payout_orders(payout_id),
    stablecoin          VARCHAR(20)     NOT NULL,
    redeemed_amount     NUMERIC(30, 8)  NOT NULL,
    fiat_received       NUMERIC(20, 8)  NOT NULL,
    fiat_currency       VARCHAR(3)      NOT NULL,
    partner             VARCHAR(100)    NOT NULL,
    partner_reference   VARCHAR(200)    NOT NULL,
    redeemed_at         TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT stablecoin_redemptions_pkey PRIMARY KEY (redemption_id)
);

CREATE INDEX stablecoin_redemptions_payout_id_idx ON stablecoin_redemptions (payout_id);

-- ============================================================
-- off_ramp_transactions
-- ============================================================
CREATE TABLE off_ramp_transactions (
    offramp_txn_id  UUID            NOT NULL DEFAULT gen_random_uuid(),
    payout_id       UUID            NOT NULL REFERENCES payout_orders(payout_id),
    partner_name    VARCHAR(100)    NOT NULL,
    event_type      VARCHAR(100)    NOT NULL,
    amount          NUMERIC(20, 8)  NOT NULL,
    currency        VARCHAR(3)      NOT NULL,
    status          VARCHAR(50)     NOT NULL,
    raw_response    JSONB           NOT NULL,
    received_at     TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT off_ramp_transactions_pkey PRIMARY KEY (offramp_txn_id)
);

CREATE INDEX off_ramp_txn_payout_id_idx ON off_ramp_transactions (payout_id, received_at DESC);

-- ============================================================
-- Namastack outbox tables (3 tables with offramp_ prefix)
-- ============================================================
CREATE TABLE offramp_outbox_record (
    id              VARCHAR(255)    NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    record_key      VARCHAR(255),
    record_type     VARCHAR(255)    NOT NULL,
    payload         TEXT            NOT NULL,
    context         TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    completed_at    TIMESTAMPTZ,
    next_retry_at   TIMESTAMPTZ,
    failure_count   INTEGER         NOT NULL DEFAULT 0,
    failure_reason  TEXT,
    partition_no    INTEGER,
    handler_id      VARCHAR(255),
    CONSTRAINT offramp_outbox_record_pkey PRIMARY KEY (id)
);

CREATE INDEX idx_offramp_outbox_record_status
    ON offramp_outbox_record (status, next_retry_at);

CREATE TABLE offramp_outbox_instance (
    instance_id     VARCHAR(255)    NOT NULL,
    hostname        VARCHAR(255),
    port            INTEGER,
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    started_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    last_heartbeat  TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT offramp_outbox_instance_pkey PRIMARY KEY (instance_id)
);

CREATE TABLE offramp_outbox_partition (
    partition_number INTEGER         NOT NULL,
    instance_id      VARCHAR(255),
    version          BIGINT          NOT NULL DEFAULT 0,
    updated_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT offramp_outbox_partition_pkey PRIMARY KEY (partition_number)
);

-- ============================================================
-- Flyway role guard for TestContainers compatibility
-- ============================================================
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sp_readonly') THEN
        GRANT SELECT ON ALL TABLES IN SCHEMA public TO sp_readonly;
    END IF;
END $$;
