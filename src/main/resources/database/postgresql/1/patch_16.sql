-- Hash bearer-shaped tokens at rest (security audit C2). Replaces the raw
-- `token` column on `account_session` and `account_token` with `token_hash`,
-- storing the lowercase hex of HMAC-SHA-256(pepper, raw token). A database
-- compromise alone no longer yields usable session bearers or recovery codes.
--
-- The plaintext column is dropped, so every existing session and pending
-- recovery token is invalidated by this migration. Users sign in again once;
-- pending password-reset / email-verification / station-delete links go dead
-- and have to be re-requested. This is documented as a one-shot upgrade event.

TRUNCATE ember_schema.account_session;
ALTER TABLE ember_schema.account_session
    DROP COLUMN token,
    ADD COLUMN token_hash CHAR(64) NOT NULL UNIQUE;

TRUNCATE ember_schema.account_token;
ALTER TABLE ember_schema.account_token
    DROP COLUMN token,
    ADD COLUMN token_hash CHAR(64) NOT NULL UNIQUE;

COMMENT ON COLUMN ember_schema.account_session.token_hash IS
    'Lowercase hex HMAC-SHA-256(auth.tokenPepper, raw bearer). 64 chars. Raw bearer is never stored.';
COMMENT ON COLUMN ember_schema.account_token.token_hash IS
    'Lowercase hex HMAC-SHA-256(auth.tokenPepper, raw token). 64 chars. Raw token is never stored.';

-- HIBP breach-check tracking (security audit H3 HIBP follow-up). Records when
-- the credential was last verified against Have I Been Pwned. The column is
-- cleared whenever the password is rotated, so a re-check fires on the next
-- login after any password change.
ALTER TABLE ember_schema.account_credential
    ADD COLUMN last_breach_check_at TIMESTAMP WITH TIME ZONE;
COMMENT ON COLUMN ember_schema.account_credential.last_breach_check_at IS
    'Timestamp of the most recent HIBP breach check for this credential. NULL when the password has never been checked or was rotated since the last check.';
