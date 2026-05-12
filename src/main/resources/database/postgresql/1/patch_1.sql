-- Add email verification status to accounts
ALTER TABLE ember_schema.account
    ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;

-- Tokens for email verification and password setup (invite flow)
CREATE TABLE ember_schema.account_token
(
    id         SERIAL PRIMARY KEY,
    account_id INTEGER   NOT NULL REFERENCES ember_schema.account (id) ON DELETE CASCADE,
    token      TEXT      NOT NULL UNIQUE,
    token_type TEXT      NOT NULL, -- verify_email, set_password
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_account_token_account ON ember_schema.account_token (account_id);
CREATE INDEX idx_account_token_type ON ember_schema.account_token (account_id, token_type);
