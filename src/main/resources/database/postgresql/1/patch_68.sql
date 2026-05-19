-- Station module toggle: if a module is in this table it is disabled
CREATE TABLE ember_schema.station_disabled_module
(
    station_id INTEGER NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    module     TEXT    NOT NULL,
    PRIMARY KEY (station_id, module)
);
