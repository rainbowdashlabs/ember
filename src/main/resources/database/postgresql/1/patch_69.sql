-- A device asking to be let in now names the account it wants, and is answered with a number.
--
-- Two things at once, because neither works without the other.
--
-- A code used to belong to nobody until somebody approved it. The approval screen only ever asked
-- whether the reader was allowed to see a step-up, so any signed-in member could approve any open
-- code and hand over their own account to whoever raised it, without either side knowing who the
-- other was. Naming the account at the start settles that: a code can only be approved by the
-- account it was raised for.
--
-- Naming the account is not enough to let the code travel inside the QR, though, because an address
-- is not a secret. Anybody can raise a request for an address they know and forward the picture.
-- What does not survive being forwarded is a number shown on the requesting screen, so the approval
-- asks for it. Six to choose from, and the wrong one ends the request rather than inviting a
-- second guess.

ALTER TABLE ember_schema.device_request
    ADD COLUMN named_account_id INTEGER     NULL REFERENCES ember_schema.account (id) ON DELETE CASCADE,
    ADD COLUMN match_number     INTEGER     NOT NULL DEFAULT 0,
    ADD COLUMN match_choices    INTEGER[]   NOT NULL DEFAULT '{}',
    ADD COLUMN rejected_at      TIMESTAMPTZ NULL;

-- The defaults exist only to fill the rows already in the table, which are a scratchpad the sweep
-- clears minutes after they expire. Every request raised from here on brings its own number.
ALTER TABLE ember_schema.device_request
    ALTER COLUMN match_number DROP DEFAULT,
    ALTER COLUMN match_choices DROP DEFAULT;

ALTER TABLE ember_schema.device_request
    -- A step-up is raised by a session that has already proved whose it is, so there is nothing for
    -- the requester to claim and nothing to check at approval.
    ADD CONSTRAINT device_request_step_up_names_nobody
        CHECK (purpose <> 'STEP_UP' OR named_account_id IS NULL),

    -- A request that was refused was never approved, and one that was approved cannot be refused
    -- afterwards. Both write the row once, and the poll reads whichever happened.
    ADD CONSTRAINT device_request_not_both_approved_and_rejected
        CHECK (rejected_at IS NULL OR approved_at IS NULL);

CREATE INDEX idx_device_request_named_account ON ember_schema.device_request (named_account_id)
    WHERE named_account_id IS NOT NULL;

COMMENT ON COLUMN ember_schema.device_request.named_account_id IS
    'The account the requesting device said it wants, resolved from the address or username typed there. Claimed and not proved, so it decides only who may approve the request and never who is signed in. NULL where the identifier matched no account, which is a request nobody can approve and which is answered exactly like a real one so that the screen cannot be used to find out which addresses exist.';
COMMENT ON COLUMN ember_schema.device_request.match_number IS
    'The two-digit number the requesting screen shows and the approving screen asks for. It never travels in the QR, which is what stops a forwarded picture being enough to approve.';
COMMENT ON COLUMN ember_schema.device_request.match_choices IS
    'The six numbers the approval screen offers, in the order it offers them, one of which is match_number. Fixed when the request is raised rather than drawn per lookup, because a fresh draw each time would leave the right number the only one appearing in every round.';
COMMENT ON COLUMN ember_schema.device_request.rejected_at IS
    'When the approving screen picked the wrong number. The request is over at that point: there is no second guess on the same code, so a guess costs a whole new request and another conversation with whoever is holding the phone.';
