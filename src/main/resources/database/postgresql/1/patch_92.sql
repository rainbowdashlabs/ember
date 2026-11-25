-- Event group fields can be tied to a field of the attendance sheet, as appointment fields can.

ALTER TABLE ember_schema.event_group_field
    ADD COLUMN attendance_field_id INT REFERENCES ember_schema.attendance_template_field (id) ON DELETE SET NULL;

COMMENT ON COLUMN ember_schema.event_group_field.field_type
    IS 'The kind of value, one an appointment field takes; member kinds included, but never answered per date.';
COMMENT ON COLUMN ember_schema.event_group_field.attendance_field_id
    IS 'The field of the event group''s attendance sheet this field fills in on the sheet of every appointment of the group. Cleared when that field is deleted, which archives it, and when the event group takes another sheet.';
