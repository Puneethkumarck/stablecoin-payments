-- V7: Namastack outbox tables for reliable event publishing
-- Prefix: gatewayiam_

CREATE TABLE gatewayiam_outbox_record (
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

CREATE INDEX idx_outbox_record_status ON gatewayiam_outbox_record (status, next_retry_at);

CREATE TABLE gatewayiam_outbox_instance (
    instance_id     VARCHAR(255) PRIMARY KEY,
    hostname        VARCHAR(255),
    port            INTEGER,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    started_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_heartbeat  TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE gatewayiam_outbox_partition (
    partition_number INTEGER PRIMARY KEY,
    instance_id      VARCHAR(255),
    version          BIGINT NOT NULL DEFAULT 0,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE gatewayiam_outbox_record IS 'Namastack outbox — reliable event publishing for api-gateway-iam';
