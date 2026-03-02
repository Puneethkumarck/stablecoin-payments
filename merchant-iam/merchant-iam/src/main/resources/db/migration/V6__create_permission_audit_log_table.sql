-- V6: Create permission_audit_log table
-- Append-only audit trail for permission and role changes
-- Partitioned by occurred_at for efficient time-range queries
-- Production: use pg_partman or cron for monthly partition creation

CREATE TABLE permission_audit_log (
    log_id          UUID            NOT NULL DEFAULT gen_random_uuid(),
    merchant_id     UUID            NOT NULL,
    user_id         UUID            NULL,
    target_user_id  UUID            NULL,
    action          VARCHAR(50)     NOT NULL,
    detail          JSONB           NOT NULL DEFAULT '{}',
    ip_address      VARCHAR(45)     NULL,
    occurred_at     TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    CONSTRAINT permission_audit_log_pkey PRIMARY KEY (log_id, occurred_at)
) PARTITION BY RANGE (occurred_at);

-- Default partition catches all rows; production should create monthly partitions
CREATE TABLE permission_audit_log_default PARTITION OF permission_audit_log DEFAULT;

-- Revoke mutating operations — this table is append-only
DO $$ BEGIN
    IF EXISTS (SELECT FROM pg_roles WHERE rolname = 'sp_user') THEN
        EXECUTE 'REVOKE UPDATE, DELETE ON permission_audit_log FROM sp_user';
    END IF;
END $$;

CREATE INDEX permission_audit_merchant_idx
    ON permission_audit_log (merchant_id, occurred_at DESC);

CREATE INDEX permission_audit_user_idx
    ON permission_audit_log (user_id, occurred_at DESC);
