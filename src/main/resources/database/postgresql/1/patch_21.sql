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

-- Fix audit-pointer foreign keys to station_member that blocked station deletion.
-- Every "created_by" / "uploaded_by" / "checked_by" pointer to station_member was
-- declared without ON DELETE, so a station delete -> cascading station_member delete
-- aborted on the first referencing row. Switch them all to ON DELETE SET NULL and
-- drop NOT NULL where it was set, so the audit trail survives the row going away
-- but no longer blocks the cascade.

ALTER TABLE ember_schema.kb_folder
    DROP CONSTRAINT kb_folder_created_by_fkey,
    ALTER COLUMN created_by DROP NOT NULL,
    ADD CONSTRAINT kb_folder_created_by_fkey FOREIGN KEY (created_by)
        REFERENCES ember_schema.station_member (id) ON DELETE SET NULL;

ALTER TABLE ember_schema.kb_file
    DROP CONSTRAINT kb_file_created_by_fkey,
    ALTER COLUMN created_by DROP NOT NULL,
    ADD CONSTRAINT kb_file_created_by_fkey FOREIGN KEY (created_by)
        REFERENCES ember_schema.station_member (id) ON DELETE SET NULL;

ALTER TABLE ember_schema.kb_file_version
    DROP CONSTRAINT kb_file_version_created_by_fkey,
    ALTER COLUMN created_by DROP NOT NULL,
    ADD CONSTRAINT kb_file_version_created_by_fkey FOREIGN KEY (created_by)
        REFERENCES ember_schema.station_member (id) ON DELETE SET NULL;

ALTER TABLE ember_schema.test_protocol_run
    DROP CONSTRAINT test_protocol_run_created_by_fkey,
    ALTER COLUMN created_by DROP NOT NULL,
    ADD CONSTRAINT test_protocol_run_created_by_fkey FOREIGN KEY (created_by)
        REFERENCES ember_schema.station_member (id) ON DELETE SET NULL;

ALTER TABLE ember_schema.test_protocol_run_member
    DROP CONSTRAINT test_protocol_run_member_locked_by_fkey,
    ADD CONSTRAINT test_protocol_run_member_locked_by_fkey FOREIGN KEY (locked_by)
        REFERENCES ember_schema.station_member (id) ON DELETE SET NULL;

ALTER TABLE ember_schema.test_protocol_run_section_done
    DROP CONSTRAINT test_protocol_run_section_done_done_by_fkey,
    ADD CONSTRAINT test_protocol_run_section_done_done_by_fkey FOREIGN KEY (done_by)
        REFERENCES ember_schema.station_member (id) ON DELETE SET NULL;

ALTER TABLE ember_schema.test_protocol_run_check
    DROP CONSTRAINT test_protocol_run_check_checked_by_fkey,
    ADD CONSTRAINT test_protocol_run_check_checked_by_fkey FOREIGN KEY (checked_by)
        REFERENCES ember_schema.station_member (id) ON DELETE SET NULL;

ALTER TABLE ember_schema.federation_lending_request
    DROP CONSTRAINT federation_lending_request_created_by_fkey,
    ALTER COLUMN created_by DROP NOT NULL,
    ADD CONSTRAINT federation_lending_request_created_by_fkey FOREIGN KEY (created_by)
        REFERENCES ember_schema.station_member (id) ON DELETE SET NULL;

ALTER TABLE ember_schema.federation_lending_message
    DROP CONSTRAINT federation_lending_message_sender_member_id_fkey,
    ADD CONSTRAINT federation_lending_message_sender_member_id_fkey FOREIGN KEY (sender_member_id)
        REFERENCES ember_schema.station_member (id) ON DELETE SET NULL;

ALTER TABLE ember_schema.lost_and_found_item
    DROP CONSTRAINT lost_and_found_item_created_by_fkey,
    ALTER COLUMN created_by DROP NOT NULL,
    ADD CONSTRAINT lost_and_found_item_created_by_fkey FOREIGN KEY (created_by)
        REFERENCES ember_schema.station_member (id) ON DELETE SET NULL;

-- Heartbeat column for transfer-token timeout. The destination instance touches this
-- on every token-authenticated GET; a watchdog scans for tokens whose last_activity_at
-- is older than 5 minutes and treats the transfer as failed (invalidates the token and
-- clears the source station's read-only-for-transfer flag), so a destination that
-- crashes or hangs mid-pull does not leave the source locked indefinitely.

ALTER TABLE ember_schema.transfer_token
    ADD COLUMN last_activity_at TIMESTAMPTZ NOT NULL DEFAULT now();

COMMENT ON COLUMN ember_schema.transfer_token.last_activity_at IS
    'Timestamp of the most recent token-authenticated request from the destination. Refreshed on every successful validateToken(). The TransferTimeoutWatchdog marks tokens stale after 5 minutes of inactivity, invalidating them and clearing the source station''s read-only-for-transfer flag.';
