-- Public waitlist: allow stations to expose waitlists for self-registration

-- Per-waitlist public flag
ALTER TABLE ember_schema.waiting_list ADD COLUMN public BOOLEAN NOT NULL DEFAULT FALSE;

-- Per-field public flag (controls visibility on public registration form)
ALTER TABLE ember_schema.waiting_list_field ADD COLUMN public BOOLEAN NOT NULL DEFAULT TRUE;

-- Station-level toggle for public waitlist feature
ALTER TABLE ember_schema.station ADD COLUMN public_waitlist_enabled BOOLEAN NOT NULL DEFAULT FALSE;

-- Pending registrations awaiting email verification
CREATE TABLE ember_schema.waitlist_verification_token (
    id           SERIAL      PRIMARY KEY,
    token        TEXT        NOT NULL UNIQUE,
    list_id      INTEGER     NOT NULL REFERENCES ember_schema.waiting_list (id) ON DELETE CASCADE,
    firstname    TEXT        NOT NULL,
    lastname     TEXT        NOT NULL DEFAULT '',
    email        TEXT        NOT NULL,
    guardians    JSONB       NOT NULL DEFAULT '[]'::jsonb,
    field_values JSONB       NOT NULL DEFAULT '{}'::jsonb,
    notes        TEXT        NOT NULL DEFAULT '',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at   TIMESTAMPTZ NOT NULL DEFAULT (now() + interval '24 hours')
);

CREATE INDEX idx_wl_verify_token ON ember_schema.waitlist_verification_token (token);
CREATE INDEX idx_wl_verify_expires ON ember_schema.waitlist_verification_token (expires_at);
