-- Add user agent and last-used tracking to sessions
ALTER TABLE ember_schema.account_session ADD COLUMN user_agent TEXT;
ALTER TABLE ember_schema.account_session ADD COLUMN last_used_at TIMESTAMP NOT NULL DEFAULT now();
