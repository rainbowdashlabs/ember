-- Track whether an attendance entry was expected (from template) or extra (manually added)
ALTER TABLE ember_schema.attendance_entry
    ADD COLUMN source TEXT NOT NULL DEFAULT 'EXPECTED';
