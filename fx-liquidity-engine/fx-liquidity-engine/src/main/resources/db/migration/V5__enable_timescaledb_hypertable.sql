-- ============================================================
-- V5: Enable TimescaleDB extension and ensure rate_history is a hypertable
-- Idempotent: safe to run on databases where V1 already created the hypertable
-- ============================================================

DO $$
BEGIN
    -- Enable TimescaleDB extension if available (pre-installed in timescale/timescaledb image)
    IF EXISTS (SELECT 1 FROM pg_available_extensions WHERE name = 'timescaledb') THEN
        CREATE EXTENSION IF NOT EXISTS timescaledb;

        -- Convert rate_history to hypertable only if not already converted
        IF NOT EXISTS (
            SELECT 1 FROM timescaledb_information.hypertables
            WHERE hypertable_name = 'rate_history'
        ) THEN
            PERFORM create_hypertable('rate_history', 'recorded_at',
                                       chunk_time_interval => INTERVAL '1 hour',
                                       migrate_data => true);
        END IF;
    END IF;
END
$$;
