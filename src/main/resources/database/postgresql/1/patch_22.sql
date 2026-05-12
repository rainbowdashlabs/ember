DROP TABLE IF EXISTS ember_schema.inventory_requirement;

CREATE TABLE ember_schema.inventory_requirement
(
    id           SERIAL PRIMARY KEY,
    inventory_id INTEGER NOT NULL REFERENCES ember_schema.inventory (id) ON DELETE CASCADE,
    role_id      INTEGER REFERENCES ember_schema.role (id) ON DELETE CASCADE,
    group_id     INTEGER REFERENCES ember_schema.member_group (id) ON DELETE CASCADE,
    quantity     INTEGER NOT NULL DEFAULT 1,
    CONSTRAINT chk_role_or_group CHECK (role_id IS NOT NULL OR group_id IS NOT NULL),
    UNIQUE (inventory_id, role_id, group_id)
);
