-- ============================================================
-- S7 Ledger & Accounting — Initial Schema
-- Three-object model: accounts + ledger_transactions + journal_entries
-- ============================================================

-- ============================================================
-- currencies  (reference table — must be created first for FKs)
-- ============================================================
CREATE TABLE currencies (
    code                VARCHAR(10)     NOT NULL,
    decimal_precision   SMALLINT        NOT NULL CHECK (decimal_precision >= 0 AND decimal_precision <= 18),
    is_active           BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT currencies_pkey PRIMARY KEY (code)
);

INSERT INTO currencies (code, decimal_precision) VALUES
    ('USD', 2), ('EUR', 2), ('GBP', 2), ('JPY', 0), ('BHD', 3),
    ('USDC', 6), ('USDT', 6), ('DAI', 18);

-- ============================================================
-- accounts  (chart of accounts — seed data)
-- ============================================================
CREATE TABLE accounts (
    account_code    VARCHAR(10)     NOT NULL,
    account_name    VARCHAR(100)    NOT NULL,
    account_type    VARCHAR(20)     NOT NULL,
    normal_balance  VARCHAR(6)      NOT NULL,
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT accounts_pkey PRIMARY KEY (account_code),
    CONSTRAINT accounts_type_check CHECK (account_type IN ('ASSET','LIABILITY','REVENUE','CLEARING','EXPENSE')),
    CONSTRAINT accounts_balance_check CHECK (normal_balance IN ('DEBIT','CREDIT'))
);

INSERT INTO accounts (account_code, account_name, account_type, normal_balance) VALUES
    ('1000', 'Fiat Receivable',         'ASSET',     'DEBIT'),
    ('1001', 'Fiat Cash',               'ASSET',     'DEBIT'),
    ('1010', 'Stablecoin Inventory',    'ASSET',     'DEBIT'),
    ('1020', 'Off-Ramp Receivable',     'ASSET',     'DEBIT'),
    ('1030', 'Stablecoin Redeemed',     'ASSET',     'DEBIT'),
    ('2000', 'Fiat Payable',            'LIABILITY', 'CREDIT'),
    ('2010', 'Client Funds Held',       'LIABILITY', 'CREDIT'),
    ('4000', 'FX Spread Revenue',       'REVENUE',   'CREDIT'),
    ('4001', 'Transaction Fee Revenue', 'REVENUE',   'CREDIT'),
    ('9000', 'In-Transit Clearing',     'CLEARING',  'DEBIT');

-- ============================================================
-- account_balances  (real-time running balance per account+currency)
-- ============================================================
CREATE TABLE account_balances (
    account_code    VARCHAR(10)     NOT NULL,
    currency        VARCHAR(10)     NOT NULL REFERENCES currencies(code),
    balance         NUMERIC(38, 18) NOT NULL DEFAULT 0,
    version         BIGINT          NOT NULL DEFAULT 0,
    last_entry_id   UUID            NULL,
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT account_balances_pkey PRIMARY KEY (account_code, currency),
    CONSTRAINT account_balances_account_fk FOREIGN KEY (account_code) REFERENCES accounts(account_code)
);

-- ============================================================
-- ledger_transactions  (groups balanced entry pairs — immutable)
-- ============================================================
CREATE TABLE ledger_transactions (
    transaction_id   UUID            NOT NULL DEFAULT gen_random_uuid(),
    payment_id       UUID            NOT NULL,
    correlation_id   UUID            NOT NULL,
    source_event     VARCHAR(100)    NOT NULL,
    source_event_id  UUID            NOT NULL,
    description      TEXT            NOT NULL,
    created_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT ledger_transactions_pkey PRIMARY KEY (transaction_id)
);

-- Idempotency: one transaction per source event
CREATE UNIQUE INDEX ledger_transactions_source_event_id_idx
    ON ledger_transactions (source_event_id);

CREATE INDEX ledger_transactions_payment_id_idx
    ON ledger_transactions (payment_id, created_at ASC);

-- Revoke modification permissions
DO $$ BEGIN
    IF EXISTS (SELECT FROM pg_tables WHERE tablename = 'ledger_transactions') THEN
        REVOKE UPDATE, DELETE ON ledger_transactions FROM PUBLIC;
    END IF;
END $$;

-- ============================================================
-- journal_entries  (append-only, partitioned by created_at)
-- ============================================================
CREATE TABLE journal_entries (
    entry_id         UUID            NOT NULL DEFAULT gen_random_uuid(),
    transaction_id   UUID            NOT NULL REFERENCES ledger_transactions(transaction_id),
    payment_id       UUID            NOT NULL,
    correlation_id   UUID            NOT NULL,
    sequence_no      INT             NOT NULL,
    entry_type       VARCHAR(6)      NOT NULL,
    account_code     VARCHAR(10)     NOT NULL REFERENCES accounts(account_code),
    amount           NUMERIC(38, 18) NOT NULL CHECK (amount > 0),
    currency         VARCHAR(10)     NOT NULL REFERENCES currencies(code),
    balance_after    NUMERIC(38, 18) NOT NULL,
    account_version  BIGINT          NOT NULL,
    source_event     VARCHAR(100)    NOT NULL,
    source_event_id  UUID            NOT NULL,
    created_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT journal_entries_pkey PRIMARY KEY (entry_id, created_at),
    CONSTRAINT journal_entry_type_check CHECK (entry_type IN ('DEBIT','CREDIT'))
) PARTITION BY RANGE (created_at);

-- Idempotency: prevent duplicate entries from Kafka retries
-- Includes partition key (created_at) for global uniqueness across partitions
CREATE UNIQUE INDEX journal_entries_idempotency_idx
    ON journal_entries (source_event_id, entry_type, account_code, created_at);

CREATE INDEX journal_entries_payment_id_idx
    ON journal_entries (payment_id, sequence_no ASC);

CREATE INDEX journal_entries_account_code_idx
    ON journal_entries (account_code, currency, created_at DESC);

CREATE INDEX journal_entries_transaction_id_idx
    ON journal_entries (transaction_id);

-- Revoke modification permissions
DO $$ BEGIN
    IF EXISTS (SELECT FROM pg_tables WHERE tablename = 'journal_entries') THEN
        REVOKE UPDATE, DELETE ON journal_entries FROM PUBLIC;
    END IF;
END $$;

-- Default partition for local dev (production uses auto-partitioning)
CREATE TABLE journal_entries_default PARTITION OF journal_entries DEFAULT;

-- ============================================================
-- reconciliation_records
-- ============================================================
CREATE TABLE reconciliation_records (
    rec_id              UUID            NOT NULL DEFAULT gen_random_uuid(),
    payment_id          UUID            NOT NULL,
    status              VARCHAR(15)     NOT NULL DEFAULT 'PENDING',
    tolerance           NUMERIC(10, 4)  NOT NULL DEFAULT 0.01,
    reconciled_at       TIMESTAMPTZ     NULL,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    version             BIGINT          NOT NULL DEFAULT 0,
    CONSTRAINT reconciliation_records_pkey PRIMARY KEY (rec_id),
    CONSTRAINT reconciliation_payment_id_unique UNIQUE (payment_id),
    CONSTRAINT reconciliation_status_check CHECK (
        status IN ('PENDING','PARTIAL','RECONCILED','DISCREPANCY')
    )
);

CREATE INDEX reconciliation_status_idx
    ON reconciliation_records (status)
    WHERE status NOT IN ('RECONCILED');

-- ============================================================
-- reconciliation_legs  (one row per leg per payment)
-- ============================================================
CREATE TABLE reconciliation_legs (
    leg_id           UUID            NOT NULL DEFAULT gen_random_uuid(),
    rec_id           UUID            NOT NULL REFERENCES reconciliation_records(rec_id),
    leg_type         VARCHAR(30)     NOT NULL,
    amount           NUMERIC(38, 18) NULL,
    currency         VARCHAR(10)     NULL REFERENCES currencies(code),
    source_event_id  UUID            NULL,
    received_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT reconciliation_legs_pkey PRIMARY KEY (leg_id),
    CONSTRAINT reconciliation_leg_type_check CHECK (
        leg_type IN ('FIAT_IN','STABLECOIN_MINTED','CHAIN_TRANSFERRED','STABLECOIN_REDEEMED','FIAT_OUT','FX_RATE')
    ),
    CONSTRAINT reconciliation_leg_unique UNIQUE (rec_id, leg_type)
);

-- ============================================================
-- audit_events  (append-only, partitioned by occurred_at)
-- ============================================================
CREATE TABLE audit_events (
    audit_id        UUID            NOT NULL DEFAULT gen_random_uuid(),
    correlation_id  UUID            NOT NULL,
    payment_id      UUID            NULL,
    service_name    VARCHAR(100)    NOT NULL,
    event_type      VARCHAR(100)    NOT NULL,
    event_payload   JSONB           NOT NULL,
    actor           VARCHAR(100)    NULL,
    occurred_at     TIMESTAMPTZ     NOT NULL,
    received_at     TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT audit_events_pkey PRIMARY KEY (audit_id, occurred_at)
) PARTITION BY RANGE (occurred_at);

DO $$ BEGIN
    IF EXISTS (SELECT FROM pg_tables WHERE tablename = 'audit_events') THEN
        REVOKE UPDATE, DELETE ON audit_events FROM PUBLIC;
    END IF;
END $$;

CREATE INDEX audit_events_payment_id_idx ON audit_events (payment_id, occurred_at DESC);
CREATE INDEX audit_events_correlation_id_idx ON audit_events (correlation_id);
CREATE INDEX audit_events_service_type_idx ON audit_events (service_name, event_type, occurred_at DESC);

-- Default partition for local dev
CREATE TABLE audit_events_default PARTITION OF audit_events DEFAULT;

-- ============================================================
-- Namastack outbox tables (3 tables with ledger_ prefix)
-- ============================================================
CREATE TABLE ledger_outbox_record (
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
    CONSTRAINT ledger_outbox_record_pkey PRIMARY KEY (id)
);

CREATE INDEX ledger_outbox_record_status_idx
    ON ledger_outbox_record (status, next_retry_at);

CREATE TABLE ledger_outbox_instance (
    instance_id     VARCHAR(255)    NOT NULL,
    hostname        VARCHAR(255),
    port            INTEGER,
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    started_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    last_heartbeat  TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT ledger_outbox_instance_pkey PRIMARY KEY (instance_id)
);

CREATE TABLE ledger_outbox_partition (
    partition_number INTEGER         NOT NULL,
    instance_id      VARCHAR(255),
    version          BIGINT          NOT NULL DEFAULT 0,
    updated_at       TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT ledger_outbox_partition_pkey PRIMARY KEY (partition_number)
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
