-- V6: Gateway audit log table (partitioned, append-only)
-- Immutable record of all gateway authentication and authorization events

CREATE TABLE gateway_audit_log (
    log_id          UUID NOT NULL DEFAULT gen_random_uuid(),
    merchant_id     UUID,
    action          VARCHAR(50) NOT NULL,
    resource        VARCHAR(255),
    source_ip       VARCHAR(45),
    detail          JSONB,
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (log_id, occurred_at)
) PARTITION BY RANGE (occurred_at);

-- Default partition for local dev
CREATE TABLE gateway_audit_log_default PARTITION OF gateway_audit_log DEFAULT;

CREATE INDEX idx_audit_log_merchant ON gateway_audit_log (merchant_id, occurred_at);
CREATE INDEX idx_audit_log_action ON gateway_audit_log (action, occurred_at);

-- Append-only: revoke UPDATE and DELETE from application role
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'sp_user') THEN
        REVOKE UPDATE, DELETE ON gateway_audit_log FROM sp_user;
    END IF;
END $$;

COMMENT ON TABLE gateway_audit_log IS 'Immutable audit log of gateway events — UPDATE and DELETE revoked';
