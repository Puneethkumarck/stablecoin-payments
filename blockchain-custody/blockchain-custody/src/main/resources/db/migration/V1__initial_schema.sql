-- ============================================================
-- S4 Blockchain & Custody — Initial Schema
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
-- wallets
-- ============================================================
CREATE TABLE wallets (
    wallet_id           UUID            NOT NULL DEFAULT gen_random_uuid(),
    chain_id            VARCHAR(20)     NOT NULL,
    address             VARCHAR(128)    NOT NULL,
    address_checksum    VARCHAR(128)    NOT NULL,
    tier                VARCHAR(10)     NOT NULL DEFAULT 'HOT',
    purpose             VARCHAR(20)     NOT NULL,
    custodian           VARCHAR(50)     NOT NULL,
    vault_account_id    VARCHAR(200)    NOT NULL,
    stablecoin          VARCHAR(20)     NOT NULL,
    is_active           BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deactivated_at      TIMESTAMPTZ     NULL,
    CONSTRAINT wallets_pkey PRIMARY KEY (wallet_id),
    CONSTRAINT wallets_address_chain_unique UNIQUE (address, chain_id),
    CONSTRAINT wallets_tier_check CHECK (tier IN ('HOT','WARM','COLD')),
    CONSTRAINT wallets_purpose_check CHECK (purpose IN ('ON_RAMP','OFF_RAMP','SWEEP','RESERVE'))
);

CREATE INDEX wallets_chain_purpose_idx
    ON wallets (chain_id, purpose)
    WHERE is_active = TRUE;

-- ============================================================
-- wallet_balances
-- ============================================================
CREATE TABLE wallet_balances (
    balance_id              UUID            NOT NULL DEFAULT gen_random_uuid(),
    wallet_id               UUID            NOT NULL REFERENCES wallets(wallet_id),
    chain_id                VARCHAR(20)     NOT NULL,
    stablecoin              VARCHAR(20)     NOT NULL,
    available_balance       NUMERIC(30, 8)  NOT NULL DEFAULT 0 CHECK (available_balance >= 0),
    reserved_balance        NUMERIC(30, 8)  NOT NULL DEFAULT 0 CHECK (reserved_balance >= 0),
    blockchain_balance      NUMERIC(30, 8)  NOT NULL DEFAULT 0 CHECK (blockchain_balance >= 0),
    last_indexed_block      NUMERIC(20, 0)  NOT NULL DEFAULT 0,
    version                 BIGINT          NOT NULL DEFAULT 0,
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT wallet_balances_pkey PRIMARY KEY (balance_id),
    CONSTRAINT wallet_balances_wallet_stablecoin_unique UNIQUE (wallet_id, stablecoin)
);

CREATE INDEX wallet_balances_wallet_id_idx ON wallet_balances (wallet_id);

-- ============================================================
-- chain_transfers
-- ============================================================
CREATE TABLE chain_transfers (
    transfer_id         UUID            NOT NULL DEFAULT gen_random_uuid(),
    payment_id          UUID            NOT NULL,
    correlation_id      UUID            NOT NULL,
    transfer_type       VARCHAR(10)     NOT NULL DEFAULT 'FORWARD',
    parent_transfer_id  UUID            NULL,
    chain_id            VARCHAR(20)     NOT NULL,
    stablecoin          VARCHAR(20)     NOT NULL,
    amount              NUMERIC(30, 8)  NOT NULL CHECK (amount > 0),
    from_wallet_id      UUID            NOT NULL REFERENCES wallets(wallet_id),
    from_address        VARCHAR(128)    NOT NULL,
    to_address          VARCHAR(128)    NOT NULL,
    nonce               NUMERIC(20, 0)  NULL,
    tx_hash             VARCHAR(128)    NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    block_number        NUMERIC(20, 0)  NULL,
    confirmations       INT             NULL,
    block_confirmed_at  TIMESTAMPTZ     NULL,
    gas_used            NUMERIC(20, 0)  NULL,
    gas_price_gwei      NUMERIC(20, 9)  NULL,
    attempt_count       INT             NOT NULL DEFAULT 0,
    failure_reason      TEXT            NULL,
    error_code          VARCHAR(100)    NULL,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT chain_transfers_pkey PRIMARY KEY (transfer_id),
    CONSTRAINT chain_transfers_payment_id_unique UNIQUE (payment_id, transfer_type),
    CONSTRAINT chain_transfers_status_check CHECK (status IN (
        'PENDING','CHAIN_SELECTED','SIGNING','SUBMITTED','RESUBMITTING',
        'CONFIRMING','CONFIRMED','FAILED'
    )),
    CONSTRAINT chain_transfers_type_check CHECK (transfer_type IN ('FORWARD','RETURN'))
);

CREATE INDEX chain_transfers_payment_id_idx
    ON chain_transfers (payment_id);

CREATE INDEX chain_transfers_tx_hash_idx
    ON chain_transfers (chain_id, tx_hash)
    WHERE tx_hash IS NOT NULL;

CREATE INDEX chain_transfers_status_idx
    ON chain_transfers (status, created_at)
    WHERE status IN ('SUBMITTED','CONFIRMING','RESUBMITTING');

-- ============================================================
-- transfer_participants (INPUT/OUTPUT/FEE per transfer)
-- ============================================================
CREATE TABLE transfer_participants (
    participant_id      UUID            NOT NULL DEFAULT gen_random_uuid(),
    transfer_id         UUID            NOT NULL REFERENCES chain_transfers(transfer_id),
    participant_type    VARCHAR(10)     NOT NULL,
    address             VARCHAR(128)    NOT NULL,
    wallet_id           UUID            NULL REFERENCES wallets(wallet_id),
    amount              NUMERIC(30, 8)  NOT NULL,
    asset_code          VARCHAR(20)     NOT NULL,
    CONSTRAINT transfer_participants_pkey PRIMARY KEY (participant_id),
    CONSTRAINT transfer_participants_type_check CHECK (participant_type IN ('INPUT','OUTPUT','FEE'))
);

CREATE INDEX transfer_participants_transfer_id_idx ON transfer_participants (transfer_id);

-- ============================================================
-- transfer_lifecycle_events (fine-grained audit trail per transfer stage)
-- ============================================================
CREATE TABLE transfer_lifecycle_events (
    event_id            UUID            NOT NULL DEFAULT gen_random_uuid(),
    transfer_id         UUID            NOT NULL REFERENCES chain_transfers(transfer_id),
    state               VARCHAR(50)     NOT NULL,
    participant_type    VARCHAR(10)     NULL,
    address             VARCHAR(128)    NULL,
    occurred_at         TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT transfer_lifecycle_events_pkey PRIMARY KEY (event_id)
);

CREATE INDEX transfer_lifecycle_events_transfer_id_idx ON transfer_lifecycle_events (transfer_id);

-- ============================================================
-- wallet_nonces (serialized nonce management per wallet)
-- ============================================================
CREATE TABLE wallet_nonces (
    wallet_id       UUID            NOT NULL REFERENCES wallets(wallet_id),
    chain_id        VARCHAR(20)     NOT NULL,
    current_nonce   NUMERIC(20, 0)  NOT NULL DEFAULT 0,
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT wallet_nonces_pkey PRIMARY KEY (wallet_id, chain_id)
);

-- ============================================================
-- chain_selection_log
-- ============================================================
CREATE TABLE chain_selection_log (
    selection_id    UUID            NOT NULL DEFAULT gen_random_uuid(),
    transfer_id     UUID            NOT NULL REFERENCES chain_transfers(transfer_id),
    evaluated_at    TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    candidates      JSONB           NOT NULL,
    selected_chain  VARCHAR(20)     NOT NULL,
    CONSTRAINT chain_selection_log_pkey PRIMARY KEY (selection_id)
);

-- ============================================================
-- wallet_status_audit_log (compliance trail for wallet lifecycle)
-- ============================================================
CREATE TABLE wallet_status_audit_log (
    audit_id        UUID            NOT NULL DEFAULT gen_random_uuid(),
    wallet_id       UUID            NOT NULL REFERENCES wallets(wallet_id),
    previous_status VARCHAR(20)     NULL,
    new_status      VARCHAR(20)     NOT NULL,
    changed_by      VARCHAR(100)    NOT NULL,
    reason          TEXT            NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT wallet_status_audit_log_pkey PRIMARY KEY (audit_id),
    CONSTRAINT wallet_status_audit_log_status_check CHECK (new_status IN ('ACTIVE','DEACTIVATED','SUSPENDED'))
);

CREATE INDEX wallet_status_audit_log_wallet_idx
    ON wallet_status_audit_log (wallet_id, created_at);

-- ============================================================
-- Namastack outbox tables (3 tables with custody_ prefix)
-- ============================================================
CREATE TABLE custody_outbox_record (
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
    CONSTRAINT custody_outbox_record_pkey PRIMARY KEY (id)
);

CREATE INDEX idx_custody_outbox_record_status
    ON custody_outbox_record (status, next_retry_at);

CREATE TABLE custody_outbox_instance (
    instance_id     VARCHAR(255)    NOT NULL,
    hostname        VARCHAR(255),
    port            INTEGER,
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    started_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    last_heartbeat  TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT custody_outbox_instance_pkey PRIMARY KEY (instance_id)
);

CREATE TABLE custody_outbox_partition (
    partition_number INTEGER         NOT NULL,
    instance_id      VARCHAR(255),
    version          BIGINT          NOT NULL DEFAULT 0,
    updated_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT custody_outbox_partition_pkey PRIMARY KEY (partition_number)
);

COMMENT ON TABLE custody_outbox_record IS 'Namastack outbox — reliable event publishing for blockchain-custody';
