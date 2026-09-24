-- A question an appointment asks can now be answered per date, and being named in one puts you on
-- the list.
--
-- A repeating appointment has kept one set of answers for the whole series, so "who drives" could
-- only ever be answered once and for all. A question may now be marked as answered per date, which
-- gives it one answer per occurrence and leaves the one on the appointment itself for every question
-- that does not vary.
--
-- The second half is who those answers name. Somebody named in a member question is taking part, and
-- until now nothing said so: they were not on the list, they were not counted, and their calendar
-- lost the appointment the moment its closing date passed. Naming them now writes them a confirmed
-- registration, which is marked so that the list can say where it came from and refuse to take it
-- back.

CREATE TABLE ember_schema.event_field_date_value
(
    field_id   INTEGER NOT NULL REFERENCES ember_schema.event_field (id) ON DELETE CASCADE,
    event_date DATE    NOT NULL,
    value      TEXT    NOT NULL DEFAULT '',
    PRIMARY KEY (field_id, event_date)
);

COMMENT ON TABLE ember_schema.event_field_date_value IS
    'The answer one question of an appointment carries on one of its dates. Only questions marked as answered per date have rows here; every other question is answered once on the appointment itself. Rows are kept when a question stops being answered per date, so switching the setting back brings the answers out again.';
COMMENT ON COLUMN ember_schema.event_field_date_value.event_date IS
    'The occurrence this answer belongs to, named after the day it falls on where the station stands, the same way a registration names one.';
COMMENT ON COLUMN ember_schema.event_field_date_value.value IS
    'The answer itself, in the same shape the question would carry on the appointment: text for a text question, the member ids for a member question.';

ALTER TABLE ember_schema.event_registration
    ADD COLUMN from_field BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN ember_schema.event_registration.from_field IS
    'Whether this registration exists because the member is named in a question of the appointment rather than because anybody signed up. It cannot be taken back while it holds: the way off the list is out of the question that names them. False on a registration somebody made, including one made by a member who is also named.';
