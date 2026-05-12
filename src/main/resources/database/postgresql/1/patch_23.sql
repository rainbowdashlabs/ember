-- Inventory Check: records a completed check of a member's inventory
CREATE TABLE ember_schema.inventory_check
(
    id         SERIAL PRIMARY KEY,
    station_id INTEGER     NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    member_id  INTEGER     NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    checked_by INTEGER     NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    checked_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_inventory_check_member ON ember_schema.inventory_check (member_id);
CREATE INDEX idx_inventory_check_station ON ember_schema.inventory_check (station_id);

-- Inventory Check Item: per-item result during a check
CREATE TABLE ember_schema.inventory_check_item
(
    id       SERIAL PRIMARY KEY,
    check_id INTEGER NOT NULL REFERENCES ember_schema.inventory_check (id) ON DELETE CASCADE,
    item_id  INTEGER NOT NULL REFERENCES ember_schema.inventory_item (id) ON DELETE CASCADE,
    result   TEXT    NOT NULL,
    note     TEXT    NOT NULL DEFAULT '',
    UNIQUE (check_id, item_id)
);

-- Occupation lock: who is currently checking whom
CREATE TABLE ember_schema.inventory_check_lock
(
    id         SERIAL PRIMARY KEY,
    station_id INTEGER     NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    member_id  INTEGER     NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE UNIQUE,
    locked_by  INTEGER     NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    locked_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
