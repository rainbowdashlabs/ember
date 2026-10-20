ALTER TABLE ember_schema.waiting_list_entry
    ADD COLUMN invited_message TEXT NOT NULL DEFAULT '';

COMMENT ON COLUMN ember_schema.waiting_list_entry.invited_message IS
    'A personal note the station wrote into the current invitation, empty when it wrote none. Shown in the invitation mail and on the page behind the entry link. Cleared together with the invitation it belongs to.';

ALTER TABLE ember_schema.waiting_list_entry_guardian
    ADD COLUMN member_id INTEGER NULL REFERENCES ember_schema.station_member (id) ON DELETE SET NULL;

CREATE INDEX idx_wle_guardian_member
    ON ember_schema.waiting_list_entry_guardian (member_id)
    WHERE member_id IS NOT NULL;

COMMENT ON COLUMN ember_schema.waiting_list_entry_guardian.member_id IS
    'The station member this guardian became when the trial began, either a new one or one already at the station they were linked to. NULL until then. Keeps a guardian from being taken on twice when the entry joins, and names which memberships ending the trial may take back.';
