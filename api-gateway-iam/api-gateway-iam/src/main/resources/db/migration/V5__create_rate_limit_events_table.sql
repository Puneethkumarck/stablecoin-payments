-- V5: Rate limit events table (partitioned by occurred_at)
-- Records rate limit hits and breaches for analytics

CREATE TABLE rate_limit_events (
    event_id        UUID NOT NULL DEFAULT gen_random_uuid(),
    merchant_id     UUID NOT NULL,
    endpoint        VARCHAR(255) NOT NULL,
    tier            VARCHAR(20) NOT NULL
                    CHECK (tier IN ('STARTER', 'GROWTH', 'ENTERPRISE', 'UNLIMITED')),
    request_count   INTEGER NOT NULL CHECK (request_count > 0),
    limit_value     INTEGER NOT NULL CHECK (limit_value > 0),
    breached        BOOLEAN NOT NULL DEFAULT FALSE,
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (event_id, occurred_at)
) PARTITION BY RANGE (occurred_at);

-- Default partition for local dev (production uses pg_partman for monthly partitions)
CREATE TABLE rate_limit_events_default PARTITION OF rate_limit_events DEFAULT;

CREATE INDEX idx_rate_limit_events_merchant ON rate_limit_events (merchant_id, occurred_at);

COMMENT ON TABLE rate_limit_events IS 'Rate limit event log — partitioned by time for efficient pruning';
