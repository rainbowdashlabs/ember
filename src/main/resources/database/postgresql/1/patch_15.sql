CREATE TABLE ember_schema.event_category
(
    id         SERIAL PRIMARY KEY,
    station_id INTEGER NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    name       TEXT    NOT NULL,
    position   INTEGER NOT NULL DEFAULT 0,
    UNIQUE (station_id, name)
);

ALTER TABLE ember_schema.station_event
    ADD COLUMN category_id INTEGER REFERENCES ember_schema.event_category (id) ON DELETE SET NULL;
