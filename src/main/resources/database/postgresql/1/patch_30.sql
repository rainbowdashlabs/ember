-- Add start date to member absences
ALTER TABLE ember_schema.member_absence ADD COLUMN absent_from DATE NOT NULL DEFAULT CURRENT_DATE;
