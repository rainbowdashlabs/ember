-- The device handshake stops being only about passkeys.
--
-- A device that is already signed in can vouch for one that is not. What the approval buys is now
-- written on the request: the right to create one passkey as before, a session, or the answer to a
-- step-up demand. The machinery either side of it is the same, so this is one table with a purpose
-- rather than three tables with the same columns.

ALTER TABLE ember_schema.passkey_device_request
    RENAME TO device_request;

ALTER INDEX ember_schema.idx_passkey_device_request_code RENAME TO idx_device_request_code;
ALTER INDEX ember_schema.idx_passkey_device_request_expires RENAME TO idx_device_request_expires;

-- The one-time token the poll hands over is no longer always an enrolment token.
ALTER TABLE ember_schema.device_request
    RENAME COLUMN enroll_token_hash TO claim_token_hash;

ALTER TABLE ember_schema.device_request
    ADD COLUMN purpose               TEXT    NOT NULL DEFAULT 'ENROL_PASSKEY',
    ADD COLUMN subject_account_id    INTEGER NULL REFERENCES ember_schema.account (id) ON DELETE CASCADE,
    ADD COLUMN requesting_account_id INTEGER NULL REFERENCES ember_schema.account (id) ON DELETE CASCADE,
    ADD COLUMN requesting_session_id INTEGER NULL REFERENCES ember_schema.account_session (id) ON DELETE CASCADE,
    ADD COLUMN step_up_category      TEXT    NULL;

-- Every request written before this patch was an enrolment whose subject was whoever approved it.
UPDATE ember_schema.device_request
SET subject_account_id = approved_account_id
WHERE approved_account_id IS NOT NULL;

-- The default exists only to fill the rows above. An insert from here on says what it is for.
ALTER TABLE ember_schema.device_request
    ALTER COLUMN purpose DROP DEFAULT;

ALTER TABLE ember_schema.device_request
    ADD CONSTRAINT device_request_purpose_known
        CHECK (purpose IN ('ENROL_PASSKEY', 'SIGN_IN', 'STEP_UP')),

    -- A step-up is raised by a session that is already signed in and knows what it is about to do.
    -- The other two purposes are raised by a device nobody has identified yet, so they carry none
    -- of it and must not be able to pretend otherwise.
    ADD CONSTRAINT device_request_step_up_is_bound
        CHECK ((purpose = 'STEP_UP') = (requesting_account_id IS NOT NULL)
            AND (purpose = 'STEP_UP') = (requesting_session_id IS NOT NULL)
            AND (purpose = 'STEP_UP') = (step_up_category IS NOT NULL)),

    -- Who the grant is for is settled at the moment of approval and never before it, so the three
    -- move together. A row with an approver and no subject would be a grant to nobody.
    ADD CONSTRAINT device_request_approval_is_whole
        CHECK ((approved_at IS NULL) = (approved_account_id IS NULL)
            AND (approved_at IS NULL) = (subject_account_id IS NULL)),

    -- A step-up stamps the session that asked for it, so it can only ever be for that account.
    -- A sign-in may be for somebody else, which is a guardian signing in a member in their care.
    ADD CONSTRAINT device_request_step_up_subject_is_requester
        CHECK (purpose <> 'STEP_UP' OR subject_account_id IS NULL OR subject_account_id = requesting_account_id);

CREATE INDEX idx_device_request_requesting_session ON ember_schema.device_request (requesting_session_id)
    WHERE requesting_session_id IS NOT NULL;

COMMENT ON TABLE ember_schema.device_request IS
    'A device asking one that is already signed in to vouch for it. What the approval buys is the purpose: one passkey, a session, or the answer to a step-up demand.';
COMMENT ON COLUMN ember_schema.device_request.purpose IS
    'What the approval buys: ENROL_PASSKEY, SIGN_IN or STEP_UP. Checked in the WHERE clause of every guarded update, so a request approved for one thing can never be spent on another.';
COMMENT ON COLUMN ember_schema.device_request.subject_account_id IS
    'The account the grant is for. Written at approval. The approver for every purpose but a sign-in a guardian made for a member in their care.';
COMMENT ON COLUMN ember_schema.device_request.requesting_account_id IS
    'The account that raised a step-up request. NULL for the purposes an unidentified device raises.';
COMMENT ON COLUMN ember_schema.device_request.requesting_session_id IS
    'The session that raised a step-up request and that a successful approval stamps. NULL for the other purposes.';
COMMENT ON COLUMN ember_schema.device_request.step_up_category IS
    'The sensitivity category the step-up was demanded for. Shown on the approval screen, and the only thing the approval buys: a confirmation given for one category does not answer a demand for another.';
COMMENT ON COLUMN ember_schema.device_request.claim_token_hash IS
    'HMAC-SHA-256 of the one-time token the poll returns after approval. It buys exactly what the purpose says and nothing else.';

-- A step-up stamp remembers only when it happened, which is not enough once a device can vouch
-- for another: a session made fresh by somebody else's approval would otherwise sail through the
-- very route that is supposed to rest on a local proof, and two sessions could then relay
-- freshness to each other for ever without anybody proving anything again.

ALTER TABLE ember_schema.account_session
    ADD COLUMN two_factor_proof TEXT NULL;

-- Nor is it enough to know that a stamp came from elsewhere. A confirmation given on another device
-- answers the demand the approver was shown and no other, or somebody talked into confirming the
-- mildest thing the product asks about would have confirmed the most sensitive one at the same time.

ALTER TABLE ember_schema.account_session
    ADD COLUMN two_factor_category TEXT NULL;

-- A session another device vouched for may never vouch in turn, however it has proved itself since.
-- Otherwise two devices could approve each other in a circle and the ladder would rest on nothing.
-- It is a property of how the session was born, so it is written once and never cleared.

ALTER TABLE ember_schema.account_session
    ADD COLUMN vouched_for BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN ember_schema.account_session.vouched_for IS
    'Whether this session exists because another device approved it rather than because somebody signed in here. Such a session may never approve a device request itself, whatever it proves afterwards.';

COMMENT ON COLUMN ember_schema.account_session.two_factor_proof IS
    'What the last step-up was answered with: TOTP, SECURITY_KEY, BACKUP_CODE, PASSKEY, PASSWORD, or ANOTHER_DEVICE. Routes that must rest on somebody proving themselves here and now refuse the last of those.';

COMMENT ON COLUMN ember_schema.account_session.two_factor_category IS
    'The category a confirmation given on another device answered. NULL where the proof was given here, which answers every category the way it always has.';

-- The request row is a scratchpad: the sweep deletes it minutes after it expires. What happened has
-- to outlive it, or an account taken over through this door could never be investigated afterwards.

ALTER TYPE ember_schema.two_factor_event ADD VALUE IF NOT EXISTS 'SIGNED_IN_VIA_DEVICE_CODE';
ALTER TYPE ember_schema.two_factor_event ADD VALUE IF NOT EXISTS 'STEPUP_VIA_DEVICE_CODE';

-- The approval and the grant being spent are up to ten minutes apart, and the interesting cases are
-- the ones where the second never happens. Without an event of its own, a grant nobody claimed and
-- a grant a revoke voided both leave no record that anybody ever said yes.
ALTER TYPE ember_schema.two_factor_event ADD VALUE IF NOT EXISTS 'DEVICE_REQUEST_APPROVED';

-- Voiding the pending requests reaches only rows that already name the account, and a sign-in names
-- nobody until it is approved. A revoke landing in the moment between the approval screen's checks
-- and its write therefore misses the row, which is then claimable by the device the revoke was meant
-- to shut out. Remembering when somebody last said stop closes that: the claim compares against it.

ALTER TABLE ember_schema.account
    ADD COLUMN sessions_revoked_at TIMESTAMP NULL;

COMMENT ON COLUMN ember_schema.account.sessions_revoked_at IS
    'When every session of this account was last ended at once. A device grant approved before that moment is refused at claim time, which catches the one a revoke could not see because it had no account on it yet.';

-- The old comment read as though five wrong codes killed a request. They never did: this counts
-- failed credential ceremonies by the asking device, and a mistyped code is thrown away without
-- ever reaching the row. What stops a guesser is the rate limiter on the approval screen.
COMMENT ON COLUMN ember_schema.device_request.attempts IS
    'Failed credential ceremonies by the requesting device. Vestigial: the claim is spent before the ceremony runs, so a request cannot live to see a second one. Wrong codes typed on the approval screen never reach this row and are throttled by the rate limiter instead.';
