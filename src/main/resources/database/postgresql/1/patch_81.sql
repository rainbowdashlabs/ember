CREATE TABLE IF NOT EXISTS ember_schema.name_change_request
(
    id           SERIAL PRIMARY KEY,
    account_id   INT         NOT NULL REFERENCES ember_schema.account (id) ON DELETE CASCADE,
    first_name   TEXT        NOT NULL,
    last_name    TEXT        NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    decided_by   INT REFERENCES ember_schema.account (id) ON DELETE SET NULL,
    decided_at   TIMESTAMPTZ,
    outcome      TEXT CHECK (outcome IN ('APPROVED', 'DENIED', 'WITHDRAWN')),
    reason       TEXT,
    CHECK ((decided_at IS NULL) = (outcome IS NULL))
);

CREATE UNIQUE INDEX IF NOT EXISTS name_change_request_open_idx
    ON ember_schema.name_change_request (account_id)
    WHERE decided_at IS NULL;

COMMENT ON TABLE ember_schema.name_change_request
    IS 'A register name a member asked for and that waits for a member manager. The name on the account only changes once a request is approved; a newer request replaces the open one.';
COMMENT ON COLUMN ember_schema.name_change_request.id
    IS 'Primary key.';
COMMENT ON COLUMN ember_schema.name_change_request.account_id
    IS 'The account whose name is to change.';
COMMENT ON COLUMN ember_schema.name_change_request.first_name
    IS 'The first name asked for.';
COMMENT ON COLUMN ember_schema.name_change_request.last_name
    IS 'The last name asked for.';
COMMENT ON COLUMN ember_schema.name_change_request.requested_at
    IS 'When the name was asked for, or last asked for again.';
COMMENT ON COLUMN ember_schema.name_change_request.decided_by
    IS 'The account that approved or denied it; null while open, for a withdrawal, and once that account is gone.';
COMMENT ON COLUMN ember_schema.name_change_request.decided_at
    IS 'When the request was approved, denied or withdrawn; null while it is open.';
COMMENT ON COLUMN ember_schema.name_change_request.outcome
    IS 'APPROVED, DENIED or WITHDRAWN; null while the request is open.';
COMMENT ON COLUMN ember_schema.name_change_request.reason
    IS 'Why a request was denied, shown to the member in the notification; optional.';
