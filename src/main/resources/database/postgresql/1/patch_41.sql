CREATE TABLE ember_schema.member_group_role (
    group_id INTEGER NOT NULL REFERENCES ember_schema.member_group(id) ON DELETE CASCADE,
    role_id INTEGER NOT NULL REFERENCES ember_schema.role(id) ON DELETE CASCADE,
    PRIMARY KEY(group_id, role_id)
);
