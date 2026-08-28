-- What a question of an event template already answers before anybody opens the appointment.
--
-- A template fixes which questions an appointment asks. Most of them are answered the same way every
-- time: the meeting point is the same yard, the responsible group is the same group. Without somewhere
-- to keep that answer, whoever writes the appointment types it again on every date of the year.
--
-- Kept apart from the answer an appointment carries, because they are different things: this is what
-- the appointment starts with, and the appointment's own value is what it ended up being. Changing the
-- template later leaves appointments already written alone.
ALTER TABLE ember_schema.event_template_field
    ADD COLUMN default_value TEXT;

COMMENT ON COLUMN ember_schema.event_template_field.default_value IS
    'What an appointment made from this template starts this question off with. Null where the question starts empty.';
