-- StableBridge Platform — Local Dev Database Init
-- Creates one database per service (mirrors production per-service DB isolation)
-- Run automatically on first postgres container startup.

-- Core payment services
CREATE DATABASE s1_payment_orchestrator;
CREATE DATABASE s2_compliance;
CREATE DATABASE s3_fiat_on_ramp;
CREATE DATABASE s4_blockchain_custody;
CREATE DATABASE s5_fiat_off_ramp;
-- S6 uses TimescaleDB (port 5433) — database 'fx_rates' created by TimescaleDB container

-- Financial infrastructure
CREATE DATABASE s7_ledger_accounting;
CREATE DATABASE s8_partner_network;
CREATE DATABASE s9_notifications;

-- Platform & identity
CREATE DATABASE s10_api_gateway_iam;
CREATE DATABASE s11_merchant_onboarding;
CREATE DATABASE s12_transaction_history;
CREATE DATABASE s13_merchant_iam;

-- Intelligence
CREATE DATABASE s14_agentic_gateway;

-- Temporal (auto-setup image creates its own schema, but needs a DB)
CREATE DATABASE temporal;

-- Grant all to dev user
GRANT ALL PRIVILEGES ON DATABASE s1_payment_orchestrator TO dev;
GRANT ALL PRIVILEGES ON DATABASE s2_compliance TO dev;
GRANT ALL PRIVILEGES ON DATABASE s3_fiat_on_ramp TO dev;
GRANT ALL PRIVILEGES ON DATABASE s4_blockchain_custody TO dev;
GRANT ALL PRIVILEGES ON DATABASE s5_fiat_off_ramp TO dev;
-- S6 uses TimescaleDB (port 5433) — grant handled by TimescaleDB container
GRANT ALL PRIVILEGES ON DATABASE s7_ledger_accounting TO dev;
GRANT ALL PRIVILEGES ON DATABASE s8_partner_network TO dev;
GRANT ALL PRIVILEGES ON DATABASE s9_notifications TO dev;
GRANT ALL PRIVILEGES ON DATABASE s10_api_gateway_iam TO dev;
GRANT ALL PRIVILEGES ON DATABASE s11_merchant_onboarding TO dev;
GRANT ALL PRIVILEGES ON DATABASE s12_transaction_history TO dev;
GRANT ALL PRIVILEGES ON DATABASE s13_merchant_iam TO dev;
GRANT ALL PRIVILEGES ON DATABASE s14_agentic_gateway TO dev;
GRANT ALL PRIVILEGES ON DATABASE temporal TO dev;
