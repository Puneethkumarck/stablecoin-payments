-- V1: Create roles table
-- Stores built-in and custom roles per merchant

DO $$ BEGIN
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'sp_user') THEN
        EXECUTE 'GRANT USAGE ON SCHEMA public TO sp_user';
    END IF;
END $$;

CREATE TABLE roles (
    role_id         UUID            NOT NULL DEFAULT gen_random_uuid(),
    merchant_id     UUID            NOT NULL,
    role_name       VARCHAR(50)     NOT NULL,
    description     TEXT            NULL,
    is_builtin      BOOLEAN         NOT NULL DEFAULT FALSE,
    is_active       BOOLEAN         NOT NULL DEFAULT TRUE,
    created_by      UUID            NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT roles_pkey PRIMARY KEY (role_id),
    CONSTRAINT roles_merchant_name_unique UNIQUE (merchant_id, role_name)
);

CREATE INDEX roles_merchant_id_idx ON roles (merchant_id) WHERE is_active = TRUE;
