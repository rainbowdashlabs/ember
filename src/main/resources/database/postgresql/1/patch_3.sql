-- Session tokens for authenticated access
CREATE TABLE ember_schema.account_session
(
    id         SERIAL PRIMARY KEY,
    account_id INTEGER   NOT NULL REFERENCES ember_schema.account (id) ON DELETE CASCADE,
    token      TEXT      NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_account_session_account ON ember_schema.account_session (account_id);
