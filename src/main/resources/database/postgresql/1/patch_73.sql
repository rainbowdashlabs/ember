-- The reminders sent about expiry dates.
--
-- One row per reminder that is done with, keyed by the day it was due rather than the day it went
-- out, so a sweep that missed a day still knows which reminder it owes. The date it was about is part
-- of the key: entering a new date starts the reminders over, and the old date's rows simply stay.
--
-- A station's fields and an association's fields number themselves separately, so a row names which
-- of the two its field belongs to. That leaves the field without a foreign key; the sweep clears the
-- rows of fields that are gone.

CREATE TABLE IF NOT EXISTS ember_schema.expiry_reminder_sent
(
    member_id     INTEGER     NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    field_origin  TEXT        NOT NULL,
    field_id      INTEGER     NOT NULL,
    expires_on    DATE        NOT NULL,
    reminder_date DATE        NOT NULL,
    sent_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (member_id, field_origin, field_id, expires_on, reminder_date)
);

CREATE INDEX IF NOT EXISTS idx_expiry_reminder_sent_field
    ON ember_schema.expiry_reminder_sent (field_origin, field_id);

COMMENT ON TABLE ember_schema.expiry_reminder_sent IS
    'Reminders about an expiry date field that are done with, so none goes out twice.';
COMMENT ON COLUMN ember_schema.expiry_reminder_sent.member_id IS
    'The member whose date the reminder was about.';
COMMENT ON COLUMN ember_schema.expiry_reminder_sent.field_origin IS
    'Whose field the reminder was about: STATION for a station''s own field, CLUSTER for an association''s.';
COMMENT ON COLUMN ember_schema.expiry_reminder_sent.field_id IS
    'The expiry date field the reminder was about, in profile_field or cluster_profile_field as field_origin says.';
COMMENT ON COLUMN ember_schema.expiry_reminder_sent.expires_on IS
    'The last valid day the reminder was about. A new date entered later starts its reminders over.';
COMMENT ON COLUMN ember_schema.expiry_reminder_sent.reminder_date IS
    'The day the reminder was due on, which a late sweep still names.';
COMMENT ON COLUMN ember_schema.expiry_reminder_sent.sent_at IS
    'When the reminder was recorded as done. A repeat after the date counts its gap from here.';
