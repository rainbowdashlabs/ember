CREATE TABLE ember_schema.attendance_template_group
(
    template_id INTEGER NOT NULL REFERENCES ember_schema.attendance_template (id) ON DELETE CASCADE,
    group_id    INTEGER NOT NULL REFERENCES ember_schema.member_group (id) ON DELETE CASCADE,
    position    INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (template_id, group_id)
);
