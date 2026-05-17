-- Event extra fields (analogous to attendance_template_field / attendance_session_field)
CREATE TABLE ember_schema.event_field
(
    id         SERIAL PRIMARY KEY,
    station_id INTEGER NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    name       TEXT    NOT NULL,
    field_type TEXT    NOT NULL,
    config     JSONB   NOT NULL DEFAULT '{}',
    position   INTEGER NOT NULL DEFAULT 0,
    UNIQUE (station_id, name)
);

CREATE TABLE ember_schema.event_field_value
(
    event_id INTEGER NOT NULL REFERENCES ember_schema.station_event (id) ON DELETE CASCADE,
    field_id INTEGER NOT NULL REFERENCES ember_schema.event_field (id) ON DELETE CASCADE,
    value    JSONB   NOT NULL DEFAULT '{}',
    PRIMARY KEY (event_id, field_id)
);
