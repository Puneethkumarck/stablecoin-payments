-- V7: Create namastack outbox tables for reliable Kafka event publishing
-- Uses namastack-outbox library (io.namastack:namastack-outbox-starter-jdbc)
-- Prefix: merchantiam_

-- ============================================================
-- 1. Outbox record table — stores event records
-- ============================================================
CREATE TABLE merchantiam_outbox_record (
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
    CONSTRAINT merchantiam_outbox_record_pkey PRIMARY KEY (id)
);

CREATE INDEX merchantiam_outbox_record_key_idx
    ON merchantiam_outbox_record (record_key, created_at);

CREATE INDEX merchantiam_outbox_record_status_partition_idx
    ON merchantiam_outbox_record (status, partition_no, next_retry_at);

CREATE INDEX merchantiam_outbox_record_created_at_idx
    ON merchantiam_outbox_record (created_at);

CREATE INDEX merchantiam_outbox_record_completed_at_idx
    ON merchantiam_outbox_record (completed_at);

CREATE INDEX merchantiam_outbox_record_status_idx
    ON merchantiam_outbox_record (status);

CREATE INDEX merchantiam_outbox_record_handler_id_idx
    ON merchantiam_outbox_record (handler_id);

-- ============================================================
-- 2. Outbox instance table — tracks active processing instances
-- ============================================================
CREATE TABLE merchantiam_outbox_instance (
    instance_id     VARCHAR(255)    NOT NULL,
    hostname        VARCHAR(255)    NOT NULL,
    port            INTEGER         NOT NULL,
    status          VARCHAR(255)    NOT NULL,
    started_at      TIMESTAMPTZ     NOT NULL,
    last_heartbeat  TIMESTAMPTZ     NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL,
    updated_at      TIMESTAMPTZ     NOT NULL,
    CONSTRAINT merchantiam_outbox_instance_pkey PRIMARY KEY (instance_id)
);

-- ============================================================
-- 3. Outbox partition table — manages partition state
-- ============================================================
CREATE TABLE merchantiam_outbox_partition (
    partition_number INTEGER         NOT NULL,
    instance_id      VARCHAR(255),
    version          BIGINT          NOT NULL DEFAULT 0,
    updated_at       TIMESTAMPTZ     NOT NULL,
    CONSTRAINT merchantiam_outbox_partition_pkey PRIMARY KEY (partition_number)
);
