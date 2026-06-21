-- Two-factor authentication tables and session extension.

CREATE TYPE ember_schema.two_factor_kind AS ENUM ('TOTP', 'WEBAUTHN', 'BACKUP_CODES');
COMMENT ON TYPE ember_schema.two_factor_kind IS 'Discriminator for the three factor families stored in account_2fa_factor.';

CREATE TYPE ember_schema.two_factor_event AS ENUM (
    'ENROLLED', 'REMOVED', 'LOGIN_VERIFIED', 'STEPUP_VERIFIED',
    'BACKUP_CODE_USED', 'BACKUP_CODE_REGENERATED',
    'ADMIN_RESET', 'TRUSTED_DEVICE_ADDED', 'TRUSTED_DEVICE_REVOKED',
    'POLICY_CHANGED'
);
COMMENT ON TYPE ember_schema.two_factor_event IS 'Audit event types for two-factor authentication lifecycle.';

-- Factor registry: one row per enrolled factor.
CREATE TABLE ember_schema.account_2fa_factor (
    id           SERIAL PRIMARY KEY,
    account_id   INTEGER NOT NULL REFERENCES ember_schema.account(id) ON DELETE CASCADE,
    kind         ember_schema.two_factor_kind NOT NULL,
    label        TEXT NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_used_at TIMESTAMPTZ NULL,
    disabled_at  TIMESTAMPTZ NULL
);
CREATE INDEX ON ember_schema.account_2fa_factor(account_id) WHERE disabled_at IS NULL;
CREATE UNIQUE INDEX ON ember_schema.account_2fa_factor(account_id, kind)
    WHERE kind IN ('TOTP', 'BACKUP_CODES') AND disabled_at IS NULL;
COMMENT ON TABLE ember_schema.account_2fa_factor IS 'Registry of enrolled 2FA factors per account. TOTP and BACKUP_CODES are singletons; WEBAUTHN may have many rows.';
COMMENT ON COLUMN ember_schema.account_2fa_factor.label IS 'User-supplied label for the factor (e.g. "Phone", "Yubikey 5C").';
COMMENT ON COLUMN ember_schema.account_2fa_factor.disabled_at IS 'Non-NULL when the factor has been soft-disabled (admin reset or user removal).';

-- TOTP secret storage (encrypted at rest).
CREATE TABLE ember_schema.account_2fa_totp (
    factor_id        INTEGER PRIMARY KEY REFERENCES ember_schema.account_2fa_factor(id) ON DELETE CASCADE,
    secret_encrypted BYTEA NOT NULL,
    secret_kid       SMALLINT NOT NULL DEFAULT 1,
    digits           SMALLINT NOT NULL DEFAULT 6,
    period_seconds   SMALLINT NOT NULL DEFAULT 30,
    algorithm        TEXT NOT NULL DEFAULT 'SHA1'
);
COMMENT ON TABLE ember_schema.account_2fa_totp IS 'TOTP secrets encrypted with AES-GCM(TWO_FACTOR_SECRET_KEY). One row per TOTP factor.';
COMMENT ON COLUMN ember_schema.account_2fa_totp.secret_encrypted IS 'AES-GCM ciphertext of the Base32-encoded TOTP secret.';
COMMENT ON COLUMN ember_schema.account_2fa_totp.secret_kid IS 'Key-ID for the encryption key, enabling future key rotation.';
COMMENT ON COLUMN ember_schema.account_2fa_totp.algorithm IS 'HMAC algorithm — SHA1 for Google Authenticator compatibility.';

-- WebAuthn credentials.
CREATE TABLE ember_schema.account_2fa_webauthn (
    factor_id           INTEGER PRIMARY KEY REFERENCES ember_schema.account_2fa_factor(id) ON DELETE CASCADE,
    credential_id       BYTEA NOT NULL,
    public_key_cose     BYTEA NOT NULL,
    signature_counter   BIGINT NOT NULL DEFAULT 0,
    aaguid              UUID NULL,
    transports          TEXT[] NOT NULL DEFAULT '{}',
    attestation_format  TEXT NULL,
    user_handle         BYTEA NOT NULL
);
CREATE UNIQUE INDEX ON ember_schema.account_2fa_webauthn(credential_id);
COMMENT ON TABLE ember_schema.account_2fa_webauthn IS 'WebAuthn/FIDO2 credential storage. Multiple rows per account for multiple security keys.';
COMMENT ON COLUMN ember_schema.account_2fa_webauthn.credential_id IS 'Credential ID from the authenticator, used to look up the key during assertion.';
COMMENT ON COLUMN ember_schema.account_2fa_webauthn.public_key_cose IS 'COSE-encoded public key for signature verification.';
COMMENT ON COLUMN ember_schema.account_2fa_webauthn.user_handle IS '64 random bytes per account, stable across credential registrations.';

-- Backup codes (hashed, single-use).
CREATE TABLE ember_schema.account_2fa_backup_code (
    id          SERIAL PRIMARY KEY,
    factor_id   INTEGER NOT NULL REFERENCES ember_schema.account_2fa_factor(id) ON DELETE CASCADE,
    code_hash   TEXT NOT NULL,
    used_at     TIMESTAMPTZ NULL,
    used_via_ip CIDR NULL
);
CREATE INDEX ON ember_schema.account_2fa_backup_code(factor_id) WHERE used_at IS NULL;
COMMENT ON TABLE ember_schema.account_2fa_backup_code IS 'Hashed backup codes (bcrypt). 10 per account, single-use.';
COMMENT ON COLUMN ember_schema.account_2fa_backup_code.code_hash IS 'bcrypt hash of the code in xxxx-xxxx-xxxx format.';

-- Trusted devices (remember-me cookie).
CREATE TABLE ember_schema.account_2fa_trusted_device (
    id            SERIAL PRIMARY KEY,
    account_id    INTEGER NOT NULL REFERENCES ember_schema.account(id) ON DELETE CASCADE,
    token_hash    TEXT NOT NULL,
    user_agent    TEXT NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    trusted_until TIMESTAMPTZ NOT NULL,
    last_seen_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at    TIMESTAMPTZ NULL
);
CREATE INDEX ON ember_schema.account_2fa_trusted_device(account_id) WHERE revoked_at IS NULL;
COMMENT ON TABLE ember_schema.account_2fa_trusted_device IS 'Trusted-device cookies allowing 2FA bypass within a time window.';
COMMENT ON COLUMN ember_schema.account_2fa_trusted_device.token_hash IS 'bcrypt hash of the cookie value. Raw cookie is never stored.';

-- Per-user-type 2FA enforcement policy.
CREATE TABLE ember_schema.two_factor_policy (
    id             SERIAL PRIMARY KEY,
    scope          TEXT NOT NULL,
    station_id     INTEGER NULL REFERENCES ember_schema.station(id) ON DELETE CASCADE,
    user_type      TEXT NULL,
    required       BOOLEAN NOT NULL,
    grace_days     SMALLINT NOT NULL DEFAULT 7,
    created_by     INTEGER NULL REFERENCES ember_schema.station_member(id) ON DELETE SET NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK ((scope = 'INSTANCE' AND station_id IS NULL)
        OR (scope = 'STATION' AND station_id IS NOT NULL))
);
CREATE UNIQUE INDEX ON ember_schema.two_factor_policy(COALESCE(station_id, 0), COALESCE(user_type, ''));
COMMENT ON TABLE ember_schema.two_factor_policy IS 'Per-StationUserType 2FA enforcement. Role-based mandate is derived at runtime, not stored here.';
COMMENT ON COLUMN ember_schema.two_factor_policy.scope IS 'INSTANCE or STATION — determines whether station_id must be set.';
COMMENT ON COLUMN ember_schema.two_factor_policy.user_type IS 'StationUserType name or NULL for all types in this scope.';

-- Audit log.
CREATE TABLE ember_schema.account_2fa_audit (
    id          SERIAL PRIMARY KEY,
    account_id  INTEGER NOT NULL REFERENCES ember_schema.account(id) ON DELETE CASCADE,
    actor_id    INTEGER NULL REFERENCES ember_schema.account(id) ON DELETE SET NULL,
    event       ember_schema.two_factor_event NOT NULL,
    factor_kind ember_schema.two_factor_kind NULL,
    user_agent  TEXT NULL,
    country     CHAR(2) NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ON ember_schema.account_2fa_audit(account_id, created_at DESC);
CREATE INDEX ON ember_schema.account_2fa_audit(actor_id, created_at DESC) WHERE actor_id IS NOT NULL;
COMMENT ON TABLE ember_schema.account_2fa_audit IS '2FA lifecycle audit trail. Retained 365 days by the same pruner as api_request_log.';
COMMENT ON COLUMN ember_schema.account_2fa_audit.actor_id IS 'NULL when account_id acted on themselves; set to the admin account for admin-initiated events.';

-- Extend account_session with 2FA verification timestamp and trusted device link.
ALTER TABLE ember_schema.account_session
    ADD COLUMN two_factor_verified_at TIMESTAMPTZ NULL,
    ADD COLUMN device_trust_id INTEGER NULL REFERENCES ember_schema.account_2fa_trusted_device(id) ON DELETE SET NULL;
COMMENT ON COLUMN ember_schema.account_session.two_factor_verified_at IS 'Last time 2FA was verified on this session. Used for step-up freshness checks.';
COMMENT ON COLUMN ember_schema.account_session.device_trust_id IS 'Link to the trusted-device row that allowed 2FA bypass on this session, if any.';
