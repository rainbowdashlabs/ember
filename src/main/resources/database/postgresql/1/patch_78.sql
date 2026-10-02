ALTER TABLE ember_schema.account_credential
    ADD COLUMN one_time_password_expires_at TIMESTAMPTZ NULL;

COMMENT ON COLUMN ember_schema.account_credential.one_time_password_expires_at IS
    'When the one-time password an administrator issued stops working. NULL once the account chose a password of its own, and for every password that was never issued as a one-time password.';

ALTER TYPE ember_schema.two_factor_event ADD VALUE IF NOT EXISTS 'ONE_TIME_PASSWORD_ISSUED';

ALTER TABLE ember_schema.account_2fa_audit
    ADD COLUMN station_id INTEGER NULL REFERENCES ember_schema.station (id) ON DELETE SET NULL;

COMMENT ON COLUMN ember_schema.account_2fa_audit.station_id IS
    'The station whose administration acted, for an event a station administrator caused. NULL for everything done by the account itself or by an instance administrator.';
