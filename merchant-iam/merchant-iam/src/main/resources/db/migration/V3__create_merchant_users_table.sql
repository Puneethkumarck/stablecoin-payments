-- V3: Create merchant_users table
-- Stores users within each merchant organisation
-- email is AES-256 encrypted (BYTEA), email_hash is SHA-256 for lookups

CREATE TABLE merchant_users (
    user_id             UUID            NOT NULL DEFAULT gen_random_uuid(),
    merchant_id         UUID            NOT NULL,
    email               BYTEA           NOT NULL,
    email_hash          VARCHAR(64)     NOT NULL,
    full_name           VARCHAR(200)    NOT NULL,
    status              VARCHAR(15)     NOT NULL DEFAULT 'INVITED',
    role_id             UUID            NOT NULL REFERENCES roles(role_id),
    mfa_enabled         BOOLEAN         NOT NULL DEFAULT FALSE,
    mfa_secret_ref      VARCHAR(200)    NULL,
    last_login_at       TIMESTAMPTZ     NULL,
    password_hash       VARCHAR(128)    NULL,
    auth_provider       VARCHAR(15)     NOT NULL DEFAULT 'LOCAL',
    invited_by          UUID            NULL,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    activated_at        TIMESTAMPTZ     NULL,
    suspended_at        TIMESTAMPTZ     NULL,
    deactivated_at      TIMESTAMPTZ     NULL,
    CONSTRAINT merchant_users_pkey PRIMARY KEY (user_id),
    CONSTRAINT merchant_users_email_merchant_unique UNIQUE (merchant_id, email_hash),
    CONSTRAINT merchant_users_status_check CHECK (
        status IN ('INVITED', 'ACTIVE', 'SUSPENDED', 'DEACTIVATED')
    ),
    CONSTRAINT merchant_users_auth_provider_check CHECK (
        auth_provider IN ('LOCAL', 'GOOGLE_SSO', 'SAML')
    )
);

CREATE INDEX merchant_users_merchant_id_idx
    ON merchant_users (merchant_id, status)
    WHERE status != 'DEACTIVATED';

CREATE INDEX merchant_users_email_hash_idx
    ON merchant_users (email_hash);

CREATE INDEX merchant_users_role_id_idx
    ON merchant_users (role_id);
