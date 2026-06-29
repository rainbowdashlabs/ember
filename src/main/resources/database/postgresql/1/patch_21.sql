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

-- Federation index pass. Every repository query in LendingRepository and FederationRepository
-- filters on one of the peer / partner / station columns below, but those columns had no index
-- beyond the primary key, so each call was a full sequential scan. Cheap fix while data is small;
-- avoids the planner falling over the moment a station has more than a few hundred federation
-- rows. The indexes below are aligned with the WHERE clauses in:
--   LendingRepository.findIncomingForStation (owning_station_id [+ status])
--   LendingRepository.findOutgoingForStation (requesting_station_id)
--   LendingRepository.countOpenIncomingForStation (owning_station_id, status)
--   LendingRepository.findMessagesForRequest (request_id ORDER BY created_at)
--   LendingRepository.findItemsForRequest (request_id ORDER BY id)
--   LendingRepository.findBlocksForStation (station_id ORDER BY block_from)
--   FederationRepository.findKbSharesForStation / findQuizSharesForStation /
--                        findProtocolSharesForStation / findInventorySharesForStation (station_id)
-- The *_share_target partner_id indexes cover reverse-lookup queries that ask "which shares is
-- this partner a recipient of" — currently linear scans of the share-target tables.

CREATE INDEX idx_federation_lending_request_requesting
    ON ember_schema.federation_lending_request (requesting_station_id);

CREATE INDEX idx_federation_lending_request_owning_status
    ON ember_schema.federation_lending_request (owning_station_id, status);

CREATE INDEX idx_federation_lending_message_request
    ON ember_schema.federation_lending_message (request_id, created_at);

CREATE INDEX idx_federation_lending_request_item_request
    ON ember_schema.federation_lending_request_item (request_id);

CREATE INDEX idx_federation_inventory_block_station
    ON ember_schema.federation_inventory_block (station_id, block_from);

CREATE INDEX idx_federation_kb_share_station
    ON ember_schema.federation_kb_share (station_id);

CREATE INDEX idx_federation_quiz_share_station
    ON ember_schema.federation_quiz_share (station_id);

CREATE INDEX idx_federation_protocol_share_station
    ON ember_schema.federation_protocol_share (station_id);

CREATE INDEX idx_federation_inventory_share_station
    ON ember_schema.federation_inventory_share (station_id);

CREATE INDEX idx_federation_kb_share_target_partner
    ON ember_schema.federation_kb_share_target (partner_id);

CREATE INDEX idx_federation_quiz_share_target_partner
    ON ember_schema.federation_quiz_share_target (partner_id);

CREATE INDEX idx_federation_protocol_share_target_partner
    ON ember_schema.federation_protocol_share_target (partner_id);

CREATE INDEX idx_federation_inventory_share_target_partner
    ON ember_schema.federation_inventory_share_target (partner_id);

-- Federation lending peer columns switch from local-int-FK to plain UUID.
-- Reason: federation_lending_request and federation_lending_message reference both sides of a
-- partnership, and one side can sit on a different Ember instance. Keeping a direct FK to the
-- local station table forced both peers to be local-only — the structural reason cross-instance
-- lending could not be modelled and the reason the rows did not survive a cross-instance station
-- transfer. Switching to station.uid (a UUID that is stable across instances) removes the
-- requirement for FK referential integrity at the schema layer; "is this station on my instance?"
-- becomes a SELECT 1 FROM station WHERE uid = ? at use time, and the federation_partner table
-- handles routing / public key / remote_host for the remote-station case (same pattern already
-- used by author_member_uid / author_station_uid on comment tables).

ALTER TABLE ember_schema.federation_lending_request
    ADD COLUMN requesting_station_uid UUID,
    ADD COLUMN owning_station_uid     UUID;

UPDATE ember_schema.federation_lending_request flr
SET requesting_station_uid = (SELECT s.uid FROM ember_schema.station s WHERE s.id = flr.requesting_station_id),
    owning_station_uid     = (SELECT s.uid FROM ember_schema.station s WHERE s.id = flr.owning_station_id);

ALTER TABLE ember_schema.federation_lending_request
    ALTER COLUMN requesting_station_uid SET NOT NULL,
    ALTER COLUMN owning_station_uid     SET NOT NULL;

ALTER TABLE ember_schema.federation_lending_request
    DROP CONSTRAINT federation_lending_request_requesting_station_id_fkey,
    DROP CONSTRAINT federation_lending_request_owning_station_id_fkey,
    DROP COLUMN requesting_station_id,
    DROP COLUMN owning_station_id;

ALTER TABLE ember_schema.federation_lending_message
    ADD COLUMN sender_station_uid UUID;

UPDATE ember_schema.federation_lending_message flm
SET sender_station_uid = (SELECT s.uid FROM ember_schema.station s WHERE s.id = flm.sender_station_id);

ALTER TABLE ember_schema.federation_lending_message
    ALTER COLUMN sender_station_uid SET NOT NULL;

ALTER TABLE ember_schema.federation_lending_message
    DROP CONSTRAINT federation_lending_message_sender_station_id_fkey,
    DROP COLUMN sender_station_id;

-- The Phase B indexes above were created against the now-dropped int columns; DROP COLUMN already
-- cascaded them out. Recreate the lookup indexes against the new uid columns so the lending list
-- queries (LendingRepository.findRequestsByStation, countActionableRequests, findLentOutByInventory)
-- stay covered.
CREATE INDEX idx_federation_lending_request_requesting
    ON ember_schema.federation_lending_request (requesting_station_uid);

CREATE INDEX idx_federation_lending_request_owning_status
    ON ember_schema.federation_lending_request (owning_station_uid, status);

ALTER TABLE ember_schema.federation_partner
    ADD COLUMN partner_station_name TEXT;

UPDATE ember_schema.federation_partner fp
SET partner_station_name = s.name
FROM ember_schema.station s
WHERE s.uid = fp.partner_station_id
  AND fp.partner_station_name IS NULL;

-- Track the station an account was created from (station invite, application acceptance, waitlist
-- registration, member import, cross-instance import). Used to pick the language of system mails
-- sent to the account, so accounts that originated from a German-language station receive German
-- mails even before they sign in for the first time. NULL for self-signup and admin bootstrap.

ALTER TABLE ember_schema.account
    ADD COLUMN creating_station_id INTEGER NULL REFERENCES ember_schema.station (id) ON DELETE SET NULL;

COMMENT ON COLUMN ember_schema.account.creating_station_id IS
    'Station that initiated the account creation (invite, application acceptance, waitlist registration, member import, cross-instance import). Used to pick the language for system mails to the account. NULL for accounts created via self-signup or the admin bootstrap.';

CREATE INDEX idx_account_creating_station
    ON ember_schema.account (creating_station_id)
    WHERE creating_station_id IS NOT NULL;

-- Setup completion timestamp. Stamped on the first successful login: the moment we have
-- evidence that the recipient of the setup mail actually reached the application. Until then
-- the account is "pending setup" and managers see it flagged in the member list with the option
-- to resend the password-setup mail. Existing accounts at migration time are backfilled to now()
-- because they have all already logged in at least once.

ALTER TABLE ember_schema.account
    ADD COLUMN setup_completed_at TIMESTAMPTZ NULL;

UPDATE ember_schema.account
SET setup_completed_at = now()
WHERE setup_completed_at IS NULL;

COMMENT ON COLUMN ember_schema.account.setup_completed_at IS
    'Timestamp of the first successful login. NULL means the account has been created but the recipient has not yet completed setup (clicked the link, set a password, signed in). Managers see pending accounts flagged in the member list.';

CREATE INDEX idx_account_setup_pending
    ON ember_schema.account (id)
    WHERE setup_completed_at IS NULL;
