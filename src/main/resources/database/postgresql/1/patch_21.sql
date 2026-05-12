DROP TABLE IF EXISTS ember_schema.inventory_member_requirement;

CREATE TABLE ember_schema.inventory_requirement
(
    id           SERIAL PRIMARY KEY,
    inventory_id INTEGER NOT NULL REFERENCES ember_schema.inventory (id) ON DELETE CASCADE,
    role_id      INTEGER NOT NULL REFERENCES ember_schema.role (id) ON DELETE CASCADE,
    quantity     INTEGER NOT NULL DEFAULT 1,
    UNIQUE (inventory_id, role_id)
);
