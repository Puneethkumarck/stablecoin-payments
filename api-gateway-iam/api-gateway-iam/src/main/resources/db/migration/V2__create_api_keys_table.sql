-- V2: API keys table
-- Stores SHA-256 hashed keys with scopes, IP allowlist, and expiration

CREATE TABLE api_keys (
    key_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id     UUID NOT NULL REFERENCES merchants(merchant_id),
    key_hash        VARCHAR(64) NOT NULL,
    key_prefix      VARCHAR(20) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    environment     VARCHAR(10) NOT NULL DEFAULT 'LIVE'
                    CHECK (environment IN ('LIVE', 'TEST')),
    scopes          TEXT[] NOT NULL DEFAULT '{}',
    allowed_ips     TEXT[] NOT NULL DEFAULT '{}',
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    expires_at      TIMESTAMPTZ,
    revoked_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX idx_api_keys_hash ON api_keys (key_hash);
CREATE INDEX idx_api_keys_merchant ON api_keys (merchant_id) WHERE active = TRUE;
CREATE INDEX idx_api_keys_prefix ON api_keys (key_prefix);

COMMENT ON TABLE api_keys IS 'API keys for merchant authentication — raw key never stored, only SHA-256 hash';
