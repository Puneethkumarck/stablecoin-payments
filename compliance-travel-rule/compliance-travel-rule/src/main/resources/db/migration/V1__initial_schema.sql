-- ============================================================
-- S2 Compliance & Travel Rule — Initial Schema
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
-- compliance_checks
-- ============================================================
CREATE TABLE compliance_checks (
    check_id            UUID            NOT NULL DEFAULT gen_random_uuid(),
    payment_id          UUID            NOT NULL,
    correlation_id      UUID            NOT NULL,
    sender_id           UUID            NOT NULL,
    recipient_id        UUID            NOT NULL,
    source_amount       NUMERIC(20, 8)  NOT NULL,
    source_currency     VARCHAR(3)      NOT NULL,
    target_currency     VARCHAR(3)      NOT NULL,
    source_country      VARCHAR(2)      NOT NULL,
    target_country      VARCHAR(2)      NOT NULL,
    status              VARCHAR(30)     NOT NULL DEFAULT 'PENDING',
    overall_result      VARCHAR(20)     NULL,
    risk_score          INT             NULL CHECK (risk_score BETWEEN 0 AND 100),
    risk_band           VARCHAR(10)     NULL,
    risk_factors        TEXT[]          NULL,
    error_code          VARCHAR(100)    NULL,
    error_message       TEXT            NULL,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    completed_at        TIMESTAMPTZ     NULL,
    expires_at          TIMESTAMPTZ     NOT NULL,
    CONSTRAINT compliance_checks_pkey PRIMARY KEY (check_id),
    CONSTRAINT compliance_checks_payment_id_unique UNIQUE (payment_id),
    CONSTRAINT compliance_checks_status_check CHECK (status IN (
        'PENDING','KYC_IN_PROGRESS','SANCTIONS_SCREENING','AML_SCREENING',
        'RISK_SCORING','TRAVEL_RULE_PACKAGING','PASSED','FAILED',
        'SANCTIONS_HIT','MANUAL_REVIEW'
    )),
    CONSTRAINT compliance_checks_result_check CHECK (
        overall_result IN ('PASSED','FAILED','MANUAL_REVIEW','SANCTIONS_HIT')
        OR overall_result IS NULL
    )
);

CREATE INDEX compliance_checks_sender_id_idx
    ON compliance_checks (sender_id, created_at DESC);

CREATE INDEX compliance_checks_status_idx
    ON compliance_checks (status, created_at)
    WHERE status NOT IN ('PASSED', 'FAILED', 'SANCTIONS_HIT');

-- ============================================================
-- kyc_results
-- ============================================================
CREATE TABLE kyc_results (
    kyc_result_id       UUID            NOT NULL DEFAULT gen_random_uuid(),
    check_id            UUID            NOT NULL REFERENCES compliance_checks(check_id),
    sender_kyc_tier     VARCHAR(20)     NOT NULL,
    sender_status       VARCHAR(20)     NOT NULL,
    recipient_status    VARCHAR(20)     NOT NULL,
    provider            VARCHAR(50)     NOT NULL,
    provider_ref        VARCHAR(200)    NOT NULL,
    raw_response        JSONB           NOT NULL DEFAULT '{}',
    checked_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT kyc_results_pkey PRIMARY KEY (kyc_result_id)
);

CREATE INDEX kyc_results_check_id_idx ON kyc_results (check_id);

-- ============================================================
-- sanctions_results
-- ============================================================
CREATE TABLE sanctions_results (
    sanctions_result_id UUID            NOT NULL DEFAULT gen_random_uuid(),
    check_id            UUID            NOT NULL REFERENCES compliance_checks(check_id),
    sender_screened     BOOLEAN         NOT NULL DEFAULT FALSE,
    recipient_screened  BOOLEAN         NOT NULL DEFAULT FALSE,
    sender_hit          BOOLEAN         NOT NULL DEFAULT FALSE,
    recipient_hit       BOOLEAN         NOT NULL DEFAULT FALSE,
    hit_details         JSONB           NULL,
    lists_checked       TEXT[]          NOT NULL,
    provider            VARCHAR(50)     NOT NULL,
    provider_ref        VARCHAR(200)    NOT NULL,
    screened_at         TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT sanctions_results_pkey PRIMARY KEY (sanctions_result_id)
);

CREATE INDEX sanctions_results_check_id_idx ON sanctions_results (check_id);
CREATE INDEX sanctions_results_hit_idx
    ON sanctions_results (screened_at DESC)
    WHERE sender_hit = TRUE OR recipient_hit = TRUE;

-- ============================================================
-- aml_results
-- ============================================================
CREATE TABLE aml_results (
    aml_result_id   UUID            NOT NULL DEFAULT gen_random_uuid(),
    check_id        UUID            NOT NULL REFERENCES compliance_checks(check_id),
    flagged         BOOLEAN         NOT NULL DEFAULT FALSE,
    flag_reasons    TEXT[]          NULL,
    chain_analysis  JSONB           NULL,
    provider        VARCHAR(50)     NOT NULL,
    provider_ref    VARCHAR(200)    NULL,
    screened_at     TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT aml_results_pkey PRIMARY KEY (aml_result_id)
);

CREATE INDEX aml_results_check_id_idx ON aml_results (check_id);
CREATE INDEX aml_results_flagged_idx
    ON aml_results (screened_at DESC)
    WHERE flagged = TRUE;

-- ============================================================
-- travel_rule_packages
-- ============================================================
CREATE TABLE travel_rule_packages (
    package_id          UUID            NOT NULL DEFAULT gen_random_uuid(),
    check_id            UUID            NOT NULL REFERENCES compliance_checks(check_id),
    originator_vasp     JSONB           NOT NULL,
    beneficiary_vasp    JSONB           NOT NULL,
    originator_data     BYTEA           NOT NULL,
    beneficiary_data    BYTEA           NOT NULL,
    protocol            VARCHAR(20)     NOT NULL,
    transmission_status VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    transmitted_at      TIMESTAMPTZ     NULL,
    protocol_ref        VARCHAR(200)    NULL,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT travel_rule_packages_pkey PRIMARY KEY (package_id),
    CONSTRAINT travel_rule_packages_check_id_unique UNIQUE (check_id)
);

-- ============================================================
-- customer_risk_profiles
-- ============================================================
CREATE TABLE customer_risk_profiles (
    customer_id         UUID            NOT NULL,
    kyc_tier            VARCHAR(20)     NOT NULL DEFAULT 'KYC_TIER_1',
    kyc_verified_at     TIMESTAMPTZ     NULL,
    risk_band           VARCHAR(10)     NOT NULL DEFAULT 'MEDIUM',
    risk_score          INT             NOT NULL DEFAULT 50 CHECK (risk_score BETWEEN 0 AND 100),
    per_txn_limit_usd   NUMERIC(20, 2)  NOT NULL DEFAULT 10000.00,
    daily_limit_usd     NUMERIC(20, 2)  NOT NULL DEFAULT 50000.00,
    monthly_limit_usd   NUMERIC(20, 2)  NOT NULL DEFAULT 500000.00,
    last_scored_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT customer_risk_profiles_pkey PRIMARY KEY (customer_id)
);

CREATE INDEX customer_risk_profiles_risk_band_idx
    ON customer_risk_profiles (risk_band, last_scored_at DESC);

-- ============================================================
-- compliance_outbox_events (Namastack outbox - service-specific prefix)
-- ============================================================
-- Namastack outbox handles relay automatically.
-- Table prefix: compliance_ (configured via OutboxHandler).
-- id column: VARCHAR(255) - Namastack uses string IDs, not UUID.
CREATE TABLE compliance_outbox_events (
    id              VARCHAR(255)    NOT NULL,
    aggregate_id    VARCHAR(255)    NOT NULL,
    aggregate_type  VARCHAR(50)     NOT NULL DEFAULT 'compliance_check',
    event_type      VARCHAR(100)    NOT NULL,
    topic           VARCHAR(200)    NOT NULL,
    partition_key   VARCHAR(128)    NOT NULL,
    payload         JSONB           NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT compliance_outbox_events_pkey PRIMARY KEY (id)
);
