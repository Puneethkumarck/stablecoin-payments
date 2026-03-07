-- V3: Replace custom outbox table with correct Namastack outbox schema
-- Prefix: compliance_

-- Drop the incorrectly structured custom outbox table
DROP TABLE IF EXISTS compliance_outbox_events;

-- Namastack outbox record table (event storage + relay)
CREATE TABLE compliance_outbox_record (
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

CREATE INDEX idx_compliance_outbox_record_status
    ON compliance_outbox_record (status, next_retry_at);

-- Namastack outbox instance tracking
CREATE TABLE compliance_outbox_instance (
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
CREATE TABLE compliance_outbox_partition (
    partition_number INTEGER PRIMARY KEY,
    instance_id      VARCHAR(255),
    version          BIGINT NOT NULL DEFAULT 0,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE compliance_outbox_record IS 'Namastack outbox — reliable event publishing for compliance-travel-rule';
