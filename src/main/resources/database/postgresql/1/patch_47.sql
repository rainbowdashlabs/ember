-- Passkeys: a credential that starts a sign-in instead of finishing one.
--
-- The WebAuthn tables so far only know credentials that are asked for after a password. A passkey
-- is the same technology pointed the other way, so the credential table learns which of the two
-- roles a row plays instead of getting a second stack. The two roles are independent flags rather
-- than one enum because a member may opt a passkey into the password path as well.
--
-- Challenges move out of account_token: a passwordless ceremony does not know the account yet, and
-- account_token.account_id is a non-null foreign key that a sign-in which has not identified
-- anybody cannot satisfy. The new table carries both the old ceremonies and the new ones, so there
-- is one challenge store rather than two.
--
-- The device request table carries the handshake by which a device that is already signed in
-- approves a new one: the new device shows a short code, an old one confirms it, and the poll that
-- follows hands the new device a token that may create exactly one credential and nothing else.

-- What a WebAuthn credential may do.

ALTER TABLE ember_schema.account_2fa_webauthn
    ADD COLUMN sign_in       BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN second_factor BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN discoverable  BOOLEAN NULL,
    ADD COLUMN user_verified BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN ember_schema.account_2fa_webauthn.sign_in IS
    'This credential may start a sign-in on its own. True for credentials created through the passkey flow.';
COMMENT ON COLUMN ember_schema.account_2fa_webauthn.second_factor IS
    'This credential is asked for after a password. True for every credential that existed before passkeys.';
COMMENT ON COLUMN ember_schema.account_2fa_webauthn.discoverable IS
    'What the credProps extension reported at creation. NULL when the authenticator did not say.';
COMMENT ON COLUMN ember_schema.account_2fa_webauthn.user_verified IS
    'Whether user verification was performed when the credential was created.';

-- Switching password sign-in off is a switch on the login path, not a deletion of the credential.

ALTER TABLE ember_schema.account_credential
    ADD COLUMN password_login_disabled_at TIMESTAMPTZ NULL;

COMMENT ON COLUMN ember_schema.account_credential.password_login_disabled_at IS
    'When the account switched password sign-in off. NULL means the password works on the login screen.';

-- The one-time passkey offer after a sign-in, and the sign-in stamp the operator report needs.

ALTER TABLE ember_schema.account
    ADD COLUMN passkey_offer_answered_at TIMESTAMPTZ NULL,
    ADD COLUMN passkey_offer_declined    BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN last_sign_in_at           TIMESTAMPTZ NULL;

COMMENT ON COLUMN ember_schema.account.passkey_offer_answered_at IS
    'When the member last answered the passkey offer. A "later" answer re-offers thirty days after this stamp.';
COMMENT ON COLUMN ember_schema.account.passkey_offer_declined IS
    'TRUE when the member declined the passkey offer for good. Never offered again while set.';
COMMENT ON COLUMN ember_schema.account.last_sign_in_at IS
    'When the account last signed in, by any method. setup_completed_at answers when somebody started, not when they were last here.';

-- One challenge store for every WebAuthn ceremony.

CREATE TABLE ember_schema.webauthn_challenge
(
    id          SERIAL PRIMARY KEY,
    token_hash  CHAR(64)    NOT NULL UNIQUE,
    purpose     TEXT        NOT NULL,
    account_id  INTEGER     NULL REFERENCES ember_schema.account (id) ON DELETE CASCADE,
    options_json TEXT       NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_webauthn_challenge_expires ON ember_schema.webauthn_challenge (expires_at);

COMMENT ON TABLE ember_schema.webauthn_challenge IS
    'Pending WebAuthn challenges, single use, short lived. Replaces the account_token rows the two-factor ceremonies used, and additionally carries ceremonies that do not know the account yet.';
COMMENT ON COLUMN ember_schema.webauthn_challenge.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.webauthn_challenge.token_hash IS
    'HMAC-SHA-256 of the challenge token handed to the browser. The raw token is never stored.';
COMMENT ON COLUMN ember_schema.webauthn_challenge.purpose IS
    'Which ceremony minted the challenge: REGISTRATION, SECOND_FACTOR_ASSERTION, PASSKEY_SIGN_IN, PASSKEY_TRIAL or DEVICE_ENROLLMENT. A challenge is only spendable at the finish of its own ceremony.';
COMMENT ON COLUMN ember_schema.webauthn_challenge.account_id IS
    'The account the ceremony belongs to. NULL for a passwordless sign-in, which does not know the account until the assertion comes back.';
COMMENT ON COLUMN ember_schema.webauthn_challenge.options_json IS
    'The serialized ceremony options the finish needs to verify the response.';
COMMENT ON COLUMN ember_schema.webauthn_challenge.created_at IS 'When the challenge was minted.';
COMMENT ON COLUMN ember_schema.webauthn_challenge.expires_at IS 'When the challenge stops being spendable.';

-- The device handshake: a new device asks, a signed-in device approves.

CREATE TABLE ember_schema.passkey_device_request
(
    id                   SERIAL PRIMARY KEY,
    code_hash            CHAR(64)    NOT NULL,
    poll_secret_hash     CHAR(64)    NOT NULL UNIQUE,
    requested_user_agent TEXT        NULL,
    requested_country    CHAR(2)     NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at           TIMESTAMPTZ NOT NULL,
    approved_account_id  INTEGER     NULL REFERENCES ember_schema.account (id) ON DELETE CASCADE,
    approved_at          TIMESTAMPTZ NULL,
    enroll_token_hash    CHAR(64)    NULL UNIQUE,
    consumed_at          TIMESTAMPTZ NULL,
    attempts             SMALLINT    NOT NULL DEFAULT 0
);

CREATE INDEX idx_passkey_device_request_code ON ember_schema.passkey_device_request (code_hash)
    WHERE consumed_at IS NULL;
CREATE INDEX idx_passkey_device_request_expires ON ember_schema.passkey_device_request (expires_at);

COMMENT ON TABLE ember_schema.passkey_device_request IS
    'A device with no passkey asking a device that is already signed in to approve it. The approval hands the new device a token that may create exactly one credential.';
COMMENT ON COLUMN ember_schema.passkey_device_request.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.passkey_device_request.code_hash IS
    'HMAC-SHA-256 of the eight-character code the new device shows and the approving member types.';
COMMENT ON COLUMN ember_schema.passkey_device_request.poll_secret_hash IS
    'HMAC-SHA-256 of the secret the requesting device polls with. Carried in the POST body, never in a path.';
COMMENT ON COLUMN ember_schema.passkey_device_request.requested_user_agent IS
    'Browser and operating system of the requesting device, shown on the approval screen.';
COMMENT ON COLUMN ember_schema.passkey_device_request.requested_country IS
    'Rough location of the requesting device, shown on the approval screen.';
COMMENT ON COLUMN ember_schema.passkey_device_request.created_at IS 'When the request was made.';
COMMENT ON COLUMN ember_schema.passkey_device_request.expires_at IS
    'When the request dies unanswered. Ten minutes, because somebody has to walk to another machine.';
COMMENT ON COLUMN ember_schema.passkey_device_request.approved_account_id IS
    'The account whose signed-in session approved the request. NULL until approved.';
COMMENT ON COLUMN ember_schema.passkey_device_request.approved_at IS 'When the request was approved.';
COMMENT ON COLUMN ember_schema.passkey_device_request.enroll_token_hash IS
    'HMAC-SHA-256 of the one-time enrolment token the poll returns after approval. The token creates one passkey and does nothing else.';
COMMENT ON COLUMN ember_schema.passkey_device_request.consumed_at IS
    'When the enrolment token was spent. Written in the same transaction as the credential it created.';
COMMENT ON COLUMN ember_schema.passkey_device_request.attempts IS
    'Failed attempts against this request. The request dies after five.';

-- New audit events. Added but not used inside this patch, which is the only restriction
-- ALTER TYPE ... ADD VALUE carries on the Postgres versions this project supports.

ALTER TYPE ember_schema.two_factor_event ADD VALUE IF NOT EXISTS 'PASSKEY_SIGN_IN';
ALTER TYPE ember_schema.two_factor_event ADD VALUE IF NOT EXISTS 'PASSKEY_ENROLLED_VIA_DEVICE_CODE';
ALTER TYPE ember_schema.two_factor_event ADD VALUE IF NOT EXISTS 'PASSWORD_LOGIN_DISABLED';
ALTER TYPE ember_schema.two_factor_event ADD VALUE IF NOT EXISTS 'PASSWORD_LOGIN_ENABLED';
ALTER TYPE ember_schema.two_factor_event ADD VALUE IF NOT EXISTS 'PASSWORD_RETIRED';
ALTER TYPE ember_schema.two_factor_event ADD VALUE IF NOT EXISTS 'STEPUP_FAILED';
