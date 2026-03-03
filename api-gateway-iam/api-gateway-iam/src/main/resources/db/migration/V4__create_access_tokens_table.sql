-- V4: Access tokens table
-- Tracks issued JWTs by JTI for revocation support

CREATE TABLE access_tokens (
    jti             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id     UUID NOT NULL REFERENCES merchants(merchant_id),
    client_id       UUID NOT NULL REFERENCES oauth_clients(client_id),
    scopes          TEXT[] NOT NULL DEFAULT '{}',
    issued_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at      TIMESTAMPTZ NOT NULL
                    CHECK (expires_at > issued_at),
    revoked         BOOLEAN NOT NULL DEFAULT FALSE,
    revoked_at      TIMESTAMPTZ
                    CHECK (revoked_at IS NULL OR revoked_at >= issued_at),
    CHECK ((revoked = TRUE AND revoked_at IS NOT NULL) OR (revoked = FALSE AND revoked_at IS NULL))
);

CREATE INDEX idx_access_tokens_merchant ON access_tokens (merchant_id);
CREATE INDEX idx_access_tokens_not_revoked ON access_tokens (expires_at) WHERE revoked = FALSE;

COMMENT ON TABLE access_tokens IS 'Issued JWT access tokens — indexed by JTI for revocation lookup';
