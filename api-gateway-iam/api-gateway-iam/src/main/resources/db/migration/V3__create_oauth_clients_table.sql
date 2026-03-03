-- V3: OAuth2 clients table
-- Supports client_credentials grant type with hashed secrets

CREATE TABLE oauth_clients (
    client_id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id         UUID NOT NULL REFERENCES merchants(merchant_id),
    client_secret_hash  VARCHAR(255) NOT NULL,
    name                VARCHAR(255) NOT NULL,
    scopes              TEXT[] NOT NULL DEFAULT '{}',
    grant_types         TEXT[] NOT NULL DEFAULT '{client_credentials}',
    active              BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    version             BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_oauth_clients_merchant ON oauth_clients (merchant_id) WHERE active = TRUE;

COMMENT ON TABLE oauth_clients IS 'OAuth2 clients for client_credentials token issuance';
