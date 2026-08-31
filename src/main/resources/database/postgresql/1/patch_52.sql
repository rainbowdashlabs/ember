-- A named set of things that belong together.
--
-- The everyday case is a box: the three games and the laminator somebody fetches for every youth
-- evening, spread over whichever inventories the pieces happened to be filed in. Nothing about those
-- rows says they belong together, and this is where that knowledge goes.
--
-- A collection is a template and carries no promise. Its lines are copied wherever they are used, so
-- editing it next month changes nothing that was already asked for, and it neither reserves nor holds
-- stock.
--
-- The line shape is deliberately the one inventory_requirement and federation_lending_request_item
-- already have, so the next thing that needs a line can share it instead of making a fourth copy. The
-- lending line allows both targets null and both set; this one does not.

CREATE TABLE ember_schema.inventory_collection
(
    id         SERIAL PRIMARY KEY,
    station_id INT         NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    name       TEXT        NOT NULL,
    note       TEXT        NOT NULL DEFAULT '',
    created_by INT REFERENCES ember_schema.station_member (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (station_id, name)
);

COMMENT ON TABLE ember_schema.inventory_collection IS
    'A named, reusable set of inventory lines. A template that is copied where it is used, never a reservation.';
COMMENT ON COLUMN ember_schema.inventory_collection.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.inventory_collection.station_id IS
    'The station the collection belongs to. An association reads the collections of its home station, so there is no second scope.';
COMMENT ON COLUMN ember_schema.inventory_collection.name IS 'What the station calls it, unique within the station.';
COMMENT ON COLUMN ember_schema.inventory_collection.note IS 'Free text about the purpose, may be empty.';
COMMENT ON COLUMN ember_schema.inventory_collection.created_by IS
    'The member who created it, or null once that member is gone.';
COMMENT ON COLUMN ember_schema.inventory_collection.created_at IS 'When it was created.';

CREATE TABLE ember_schema.inventory_collection_line
(
    id            SERIAL PRIMARY KEY,
    collection_id INT NOT NULL REFERENCES ember_schema.inventory_collection (id) ON DELETE CASCADE,
    item_id       INT REFERENCES ember_schema.inventory_item (id) ON DELETE CASCADE,
    inventory_id  INT REFERENCES ember_schema.inventory (id) ON DELETE CASCADE,
    quantity      INT NOT NULL DEFAULT 1,
    position      INT NOT NULL DEFAULT 0,
    CONSTRAINT chk_collection_line_one_target CHECK (num_nonnulls(item_id, inventory_id) = 1),
    CONSTRAINT chk_collection_line_quantity CHECK (quantity >= 1),
    CONSTRAINT chk_collection_line_named_item_single CHECK (item_id IS NULL OR quantity = 1)
);

CREATE INDEX idx_inventory_collection_line_collection
    ON ember_schema.inventory_collection_line (collection_id);
CREATE INDEX idx_inventory_collection_line_item
    ON ember_schema.inventory_collection_line (item_id) WHERE item_id IS NOT NULL;
CREATE INDEX idx_inventory_collection_line_inventory
    ON ember_schema.inventory_collection_line (inventory_id) WHERE inventory_id IS NOT NULL;

COMMENT ON TABLE ember_schema.inventory_collection_line IS
    'One line of a collection: either a named piece or a count out of an inventory, never both and never neither.';
COMMENT ON COLUMN ember_schema.inventory_collection_line.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.inventory_collection_line.collection_id IS 'References the collection.';
COMMENT ON COLUMN ember_schema.inventory_collection_line.item_id IS
    'The named piece this line asks for. Exactly one of item_id/inventory_id is set. The line goes when the piece goes.';
COMMENT ON COLUMN ember_schema.inventory_collection_line.inventory_id IS
    'The inventory a counted line draws from. Exactly one of item_id/inventory_id is set.';
COMMENT ON COLUMN ember_schema.inventory_collection_line.quantity IS
    'How many pieces the line asks for. Always 1 on a named-item line, because a named piece is one piece.';
COMMENT ON COLUMN ember_schema.inventory_collection_line.position IS 'Display order within the collection.';
