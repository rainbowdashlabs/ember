-- The evening a procedure was prepared for.
--
-- A preparation list made out of who is coming is about one appointment on one date, and until now
-- nothing recorded that. The list merely carried the appointment's name in its title, which reads
-- like a connection and is not one: nothing could lead back to the appointment, and nothing could
-- tell that a second press of the same button would make a second list for the very same evening.
--
-- Both columns are set together or not at all. A procedure written by hand keeps them empty and
-- behaves exactly as it did before. Losing the appointment leaves the procedure standing with its
-- steps and its progress intact, so the reference clears itself rather than taking the list with it.

ALTER TABLE ember_schema.procedure
    ADD COLUMN event_id   INTEGER REFERENCES ember_schema.station_event (id) ON DELETE SET NULL,
    ADD COLUMN event_date DATE;

CREATE INDEX procedure_event_idx ON ember_schema.procedure (event_id, event_date);

COMMENT ON COLUMN ember_schema.procedure.event_id IS
    'The appointment this procedure was prepared for, or NULL when it was written by hand. Cleared rather than cascaded when the appointment is deleted, so the procedure and its recorded progress survive it.';

COMMENT ON COLUMN ember_schema.procedure.event_date IS
    'The single occurrence of that appointment the procedure belongs to, since a recurring appointment has one per date. NULL exactly when event_id is NULL.';
