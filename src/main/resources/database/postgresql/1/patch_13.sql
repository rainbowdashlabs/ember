INSERT INTO ember_schema.role (name) VALUES ('event_management') ON CONFLICT DO NOTHING;

CREATE TABLE ember_schema.station_event
(
    id          SERIAL PRIMARY KEY,
    station_id  INTEGER NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    name        TEXT    NOT NULL,
    description TEXT,
    event_type  TEXT    NOT NULL DEFAULT 'ONE_TIME',
    day_of_week INTEGER,
    event_date  DATE,
    start_time  TIME    NOT NULL,
    end_time    TIME    NOT NULL,
    template_id INTEGER REFERENCES ember_schema.attendance_template (id) ON DELETE SET NULL
);

CREATE INDEX idx_station_event_station ON ember_schema.station_event (station_id);

CREATE TABLE ember_schema.station_event_break
(
    id         SERIAL PRIMARY KEY,
    station_id INTEGER NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    name       TEXT    NOT NULL,
    start_date DATE    NOT NULL,
    end_date   DATE    NOT NULL
);

CREATE INDEX idx_station_event_break_station ON ember_schema.station_event_break (station_id);
CREATE INDEX idx_station_event_break_dates ON ember_schema.station_event_break (start_date, end_date);
