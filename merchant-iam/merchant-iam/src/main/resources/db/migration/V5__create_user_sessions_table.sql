-- V5: Create user_sessions table
-- Tracks active JWT sessions with revocation support

CREATE TABLE user_sessions (
    session_id      UUID            NOT NULL DEFAULT gen_random_uuid(),
    user_id         UUID            NOT NULL REFERENCES merchant_users(user_id),
    merchant_id     UUID            NOT NULL,
    ip_address      VARCHAR(45)     NOT NULL,
    user_agent      TEXT            NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ     NOT NULL,
    last_active_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    revoked         BOOLEAN         NOT NULL DEFAULT FALSE,
    revoked_at      TIMESTAMPTZ     NULL,
    revoke_reason   VARCHAR(50)     NULL,
    CONSTRAINT user_sessions_pkey PRIMARY KEY (session_id)
);

CREATE INDEX user_sessions_user_id_idx
    ON user_sessions (user_id, created_at DESC)
    WHERE revoked = FALSE;

CREATE INDEX user_sessions_merchant_id_idx
    ON user_sessions (merchant_id)
    WHERE revoked = FALSE;

CREATE INDEX user_sessions_expires_at_idx
    ON user_sessions (expires_at)
    WHERE revoked = FALSE;
