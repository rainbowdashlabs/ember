-- GDPR consent proof tracking
CREATE TABLE ember_schema.gdpr_consent
(
    id           SERIAL PRIMARY KEY,
    account_id   INTEGER   NOT NULL REFERENCES ember_schema.account (id) ON DELETE CASCADE,
    consent_version TEXT   NOT NULL,
    ip_address   TEXT,
    country      TEXT,
    user_agent   TEXT,
    consented_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_gdpr_consent_account ON ember_schema.gdpr_consent (account_id);

-- Add location (country from Cloudflare geolocation) to sessions
ALTER TABLE ember_schema.account_session
    ADD COLUMN location TEXT;
