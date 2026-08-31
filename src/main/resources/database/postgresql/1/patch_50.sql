ALTER TABLE ember_schema.checklist
    ADD COLUMN source_event_id   INTEGER NULL REFERENCES ember_schema.station_event (id) ON DELETE SET NULL,
    ADD COLUMN source_event_date DATE    NULL,
    ADD CONSTRAINT checklist_source_event_needs_date CHECK (source_event_id IS NULL OR source_event_date IS NOT NULL);

CREATE INDEX idx_checklist_source_event ON ember_schema.checklist (source_event_id);

COMMENT ON COLUMN ember_schema.checklist.source_event_id IS
    'The appointment this checklist follows instead of a filter, or NULL when it follows the rows in checklist_member_filter. Set means the two are exclusive: the filter table is empty and refresh resolves the accepted sign-ups of the occurrence instead. Deleting the appointment sets this back to NULL, which keeps every row already on the list and stops the list following anything.';
COMMENT ON COLUMN ember_schema.checklist.source_event_date IS
    'The one date of that appointment whose sign-ups are followed. Registrations are kept per appointment and date, so an appointment without a date would resolve to the union of every occurrence there has ever been. A leftover date with no appointment is what a deleted appointment leaves behind and carries no meaning.';
