-- V14: Migrate from custom outbox to namastack outbox pattern
-- Prefix: onboarding_

-- ============================================================
-- 1. Drop old custom outbox table
-- ============================================================
DROP INDEX IF EXISTS idx_outbox_events_unprocessed;
DROP TABLE IF EXISTS outbox_events;

-- ============================================================
-- 2. Outbox record table — stores event records
-- ============================================================
CREATE TABLE onboarding_outbox_record (
    id              VARCHAR(255)    NOT NULL,
    status          VARCHAR(255)    NOT NULL,
    record_key      VARCHAR(255)    NOT NULL,
    record_type     VARCHAR(255)    NOT NULL,
    payload         TEXT            NOT NULL,
    context         TEXT,
    created_at      TIMESTAMPTZ     NOT NULL,
    completed_at    TIMESTAMPTZ,
    next_retry_at   TIMESTAMPTZ,
    failure_count   INTEGER         NOT NULL DEFAULT 0,
    failure_reason  TEXT,
    partition_no    INTEGER         NOT NULL,
    handler_id      VARCHAR(255),
    CONSTRAINT onboarding_outbox_record_pkey PRIMARY KEY (id)
);

CREATE INDEX onboarding_outbox_record_key_idx
    ON onboarding_outbox_record (record_key, created_at);

CREATE INDEX onboarding_outbox_record_status_partition_idx
    ON onboarding_outbox_record (status, partition_no, next_retry_at);

CREATE INDEX onboarding_outbox_record_created_at_idx
    ON onboarding_outbox_record (created_at);

CREATE INDEX onboarding_outbox_record_completed_at_idx
    ON onboarding_outbox_record (completed_at);

CREATE INDEX onboarding_outbox_record_status_idx
    ON onboarding_outbox_record (status);

CREATE INDEX onboarding_outbox_record_handler_id_idx
    ON onboarding_outbox_record (handler_id);

-- ============================================================
-- 3. Outbox instance table — tracks active processing instances
-- ============================================================
CREATE TABLE onboarding_outbox_instance (
    instance_id     VARCHAR(255)    NOT NULL,
    hostname        VARCHAR(255)    NOT NULL,
    port            INTEGER         NOT NULL,
    status          VARCHAR(255)    NOT NULL,
    started_at      TIMESTAMPTZ     NOT NULL,
    last_heartbeat  TIMESTAMPTZ     NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL,
    updated_at      TIMESTAMPTZ     NOT NULL,
    CONSTRAINT onboarding_outbox_instance_pkey PRIMARY KEY (instance_id)
);

-- ============================================================
-- 4. Outbox partition table — manages partition state
-- ============================================================
CREATE TABLE onboarding_outbox_partition (
    partition_number INTEGER         NOT NULL,
    instance_id      VARCHAR(255),
    version          BIGINT          NOT NULL DEFAULT 0,
    updated_at       TIMESTAMPTZ     NOT NULL,
    CONSTRAINT onboarding_outbox_partition_pkey PRIMARY KEY (partition_number)
);
