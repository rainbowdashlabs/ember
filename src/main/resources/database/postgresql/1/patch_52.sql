ALTER TABLE ember_schema.waiting_list_entry
    ADD COLUMN invited_event_id     INTEGER NULL REFERENCES ember_schema.station_event (id) ON DELETE SET NULL,
    ADD COLUMN invited_event_date   DATE    NULL,
    ADD COLUMN invited_arrival_time TIME    NULL,
    ADD CONSTRAINT waiting_list_entry_invited_event_needs_date
        CHECK (invited_event_id IS NULL OR invited_event_date IS NOT NULL);

CREATE INDEX idx_waiting_list_entry_invited_event
    ON ember_schema.waiting_list_entry (invited_event_id, invited_event_date)
    WHERE invited_event_id IS NOT NULL;

COMMENT ON COLUMN ember_schema.waiting_list_entry.invited_event_id IS
    'The appointment the current invitation asks them to come to, or NULL when nobody has been invited yet. No sign-up is created from it: they have not joined anything, so they are not on the attendee list and count towards nothing the station plans from. Deleting the appointment empties this, which leaves the invitation without an occasion rather than pointing at nothing.';
COMMENT ON COLUMN ember_schema.waiting_list_entry.invited_event_date IS
    'The one date of that appointment they were invited to. An appointment repeats, so without a date the invitation would name every Tuesday there has ever been, and the answer to it would mean nothing.';
COMMENT ON COLUMN ember_schema.waiting_list_entry.invited_arrival_time IS
    'When they were asked to be there, which is usually earlier than everybody else so somebody can meet them. NULL when the invitation named no time of its own. A time rather than an offset, because that is what the mail has to say and what the reader has to read.';
