-- V4: Create invitations table
-- Stores team member invitations with hashed tokens and expiry

CREATE TABLE invitations (
    invitation_id   UUID            NOT NULL DEFAULT gen_random_uuid(),
    merchant_id     UUID            NOT NULL,
    email           BYTEA           NOT NULL,
    email_hash      VARCHAR(64)     NOT NULL,
    role_id         UUID            NOT NULL REFERENCES roles(role_id),
    invited_by      UUID            NOT NULL,
    token_hash      VARCHAR(128)    NOT NULL,
    status          VARCHAR(10)     NOT NULL DEFAULT 'PENDING',
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ     NOT NULL,
    accepted_at     TIMESTAMPTZ     NULL,
    CONSTRAINT invitations_pkey PRIMARY KEY (invitation_id),
    CONSTRAINT invitations_token_hash_unique UNIQUE (token_hash),
    CONSTRAINT invitations_status_check CHECK (
        status IN ('PENDING', 'ACCEPTED', 'EXPIRED', 'REVOKED')
    )
);

CREATE INDEX invitations_merchant_id_idx
    ON invitations (merchant_id, status)
    WHERE status = 'PENDING';

CREATE INDEX invitations_email_hash_idx
    ON invitations (email_hash);

CREATE INDEX invitations_expires_at_idx
    ON invitations (expires_at)
    WHERE status = 'PENDING';
