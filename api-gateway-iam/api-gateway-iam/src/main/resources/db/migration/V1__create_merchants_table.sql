-- V1: Platform-level merchants table (S10 API Gateway)
-- Tracks merchant status, KYB verification, rate limit tier, and allowed corridors

CREATE TABLE merchants (
    merchant_id     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    external_id     UUID NOT NULL UNIQUE,
    name            VARCHAR(255) NOT NULL,
    country         VARCHAR(3) NOT NULL,
    scopes          TEXT[] NOT NULL DEFAULT '{}',
    corridors       JSONB NOT NULL DEFAULT '[]',
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING', 'ACTIVE', 'SUSPENDED', 'CLOSED')),
    kyb_status      VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                    CHECK (kyb_status IN ('PENDING', 'VERIFIED', 'REJECTED')),
    rate_limit_tier VARCHAR(20) NOT NULL DEFAULT 'STARTER'
                    CHECK (rate_limit_tier IN ('STARTER', 'GROWTH', 'ENTERPRISE', 'UNLIMITED')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_merchants_status ON merchants (status) WHERE status = 'ACTIVE';
CREATE INDEX idx_merchants_external_id ON merchants (external_id);

COMMENT ON TABLE merchants IS 'Platform-level merchant registry for API Gateway access control';
