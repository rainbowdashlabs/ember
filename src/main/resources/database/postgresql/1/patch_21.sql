ALTER TABLE ember_schema.station
    ADD COLUMN setup_completed_at TIMESTAMPTZ NULL;

COMMENT ON COLUMN ember_schema.station.setup_completed_at IS
    'Timestamp when an administrator first marked the station setup wizard as complete. NULL means the wizard still runs for administrators logging in, and the dashboard setup checklist is shown.';

CREATE TABLE ember_schema.station_member_invite
(
    id                    SERIAL PRIMARY KEY,
    station_id            INTEGER     NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    token                 TEXT        NOT NULL UNIQUE,
    email                 TEXT        NOT NULL,
    first_name            TEXT        NOT NULL,
    last_name             TEXT        NOT NULL,
    user_type             TEXT        NOT NULL,
    group_id              INTEGER     NULL REFERENCES ember_schema.member_group (id) ON DELETE SET NULL,
    guardian_of_invite_id INTEGER     NULL REFERENCES ember_schema.station_member_invite (id) ON DELETE CASCADE,
    invited_by_member_id  INTEGER     NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    expires_at            TIMESTAMPTZ NOT NULL,
    accepted_at           TIMESTAMPTZ NULL,
    accepted_account_id   INTEGER     NULL REFERENCES ember_schema.account (id) ON DELETE SET NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_station_member_invite_station ON ember_schema.station_member_invite (station_id);
CREATE INDEX idx_station_member_invite_pending ON ember_schema.station_member_invite (station_id) WHERE accepted_at IS NULL;
CREATE INDEX idx_station_member_invite_guardian_of ON ember_schema.station_member_invite (guardian_of_invite_id) WHERE guardian_of_invite_id IS NOT NULL;

COMMENT ON TABLE ember_schema.station_member_invite IS
    'Single-use email invite a station administrator sends to bring a new member onto the station. The invitee follows the tokenised link, sets a password, and is created as an account + station_member in one step. Distinct from the public waiting-list invite, which is a shared link without a designated recipient.';
COMMENT ON COLUMN ember_schema.station_member_invite.token IS
    'URL-safe Base64-encoded random token used in the public invite link. Plain (not hashed) — short-lived and single-use, matching the existing public registration token model.';
COMMENT ON COLUMN ember_schema.station_member_invite.user_type IS
    'Target StationUserType (MEMBER, GUARDIAN, TEAM, MANAGER) the new station_member row will be assigned on acceptance.';
COMMENT ON COLUMN ember_schema.station_member_invite.guardian_of_invite_id IS
    'When non-null, this invite is for a guardian whose managed member is the account that accepts the referenced invite. The guardian-of relation is wired on whichever side accepts last.';
COMMENT ON COLUMN ember_schema.station_member_invite.group_id IS
    'Optional member group the new station_member row will be added to on acceptance. ON DELETE SET NULL so deleting a group does not invalidate pending invites.';
COMMENT ON COLUMN ember_schema.station_member_invite.accepted_account_id IS
    'Account row created (or chosen) at acceptance time. NULL while pending.';
