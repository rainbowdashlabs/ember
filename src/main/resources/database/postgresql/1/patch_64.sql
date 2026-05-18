-- Migrate event fields from station-level to per-event
-- Each event now defines its own fields with values inline

DROP TABLE IF EXISTS ember_schema.event_field_value;
DROP TABLE IF EXISTS ember_schema.event_field;

CREATE TABLE ember_schema.event_field
(
    id       SERIAL PRIMARY KEY,
    event_id INTEGER NOT NULL REFERENCES ember_schema.station_event (id) ON DELETE CASCADE,
    name     TEXT    NOT NULL,
    value    TEXT    NOT NULL DEFAULT '',
    position INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_event_field_event ON ember_schema.event_field (event_id);
