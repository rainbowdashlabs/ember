-- Migrate attendance_entry status to proper enum values
UPDATE ember_schema.attendance_entry SET status = 'PRESENT' WHERE status = 'present';
UPDATE ember_schema.attendance_entry SET status = 'ABSENT' WHERE status = 'absent';
-- Any other values become PRESENT
UPDATE ember_schema.attendance_entry SET status = 'PRESENT' WHERE status NOT IN ('PRESENT', 'ABSENT', 'DECLINED');
