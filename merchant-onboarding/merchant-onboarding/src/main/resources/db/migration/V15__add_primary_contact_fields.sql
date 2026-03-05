-- Add primary contact fields for downstream services (S13, S10)
ALTER TABLE merchants ADD COLUMN IF NOT EXISTS primary_contact_email VARCHAR(320);
ALTER TABLE merchants ADD COLUMN IF NOT EXISTS primary_contact_name  VARCHAR(255);
