-- V7: Create outbox_events table
-- Transactional outbox for reliable Kafka event publishing
-- Same pattern as S11 Merchant Onboarding

CREATE TABLE outbox_events (
    event_id        UUID            NOT NULL DEFAULT gen_random_uuid(),
    aggregate_id    UUID            NOT NULL,
    aggregate_type  VARCHAR(50)     NOT NULL DEFAULT 'merchant_team',
    event_type      VARCHAR(100)    NOT NULL,
    topic           VARCHAR(200)    NOT NULL,
    partition_key   VARCHAR(128)    NOT NULL,
    payload         JSONB           NOT NULL,
    published       BOOLEAN         NOT NULL DEFAULT FALSE,
    published_at    TIMESTAMPTZ     NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT outbox_events_pkey PRIMARY KEY (event_id)
);

CREATE INDEX outbox_unpublished_idx
    ON outbox_events (created_at ASC) WHERE published = FALSE;
