CREATE TABLE member_group_role (
    group_id INTEGER NOT NULL REFERENCES member_group(id) ON DELETE CASCADE,
    role_id INTEGER NOT NULL REFERENCES role(id) ON DELETE CASCADE,
    PRIMARY KEY(group_id, role_id)
);
