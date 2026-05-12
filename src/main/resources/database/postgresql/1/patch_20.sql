CREATE TABLE ember_schema.inventory_member_requirement
(
    id           SERIAL PRIMARY KEY,
    inventory_id INTEGER NOT NULL REFERENCES ember_schema.inventory (id) ON DELETE CASCADE,
    member_id    INTEGER NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    size_id      INTEGER REFERENCES ember_schema.inventory_size (id) ON DELETE SET NULL,
    quantity     INTEGER NOT NULL DEFAULT 1
);

CREATE INDEX idx_inventory_member_req_inventory ON ember_schema.inventory_member_requirement (inventory_id);
CREATE INDEX idx_inventory_member_req_member ON ember_schema.inventory_member_requirement (member_id);
