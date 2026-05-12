CREATE TABLE ember_schema.inventory_item_history
(
    id          SERIAL PRIMARY KEY,
    item_id     INTEGER   NOT NULL REFERENCES ember_schema.inventory_item (id) ON DELETE CASCADE,
    member_id   INTEGER   REFERENCES ember_schema.station_member (id) ON DELETE SET NULL,
    member_name TEXT      NOT NULL DEFAULT '',
    given_out   TIMESTAMP NOT NULL DEFAULT NOW(),
    returned    TIMESTAMP
);

CREATE INDEX idx_inventory_item_history_item ON ember_schema.inventory_item_history (item_id);
CREATE INDEX idx_inventory_item_history_member ON ember_schema.inventory_item_history (member_id);
