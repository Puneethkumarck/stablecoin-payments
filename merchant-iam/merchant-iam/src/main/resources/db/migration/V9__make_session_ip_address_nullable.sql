-- V9: Make ip_address nullable — sessions are now persisted from domain layer
-- which has no access to HTTP request metadata. IP can be set later via adapter if needed.
ALTER TABLE user_sessions ALTER COLUMN ip_address DROP NOT NULL;
