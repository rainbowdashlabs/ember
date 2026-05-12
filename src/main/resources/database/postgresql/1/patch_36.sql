-- Add attendance export manager role
INSERT INTO ember_schema.role (name) VALUES ('attendance_export_manager') ON CONFLICT DO NOTHING;
