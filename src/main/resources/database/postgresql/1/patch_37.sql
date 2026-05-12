-- Saved attendance report presets
CREATE TABLE ember_schema.attendance_report_preset
(
    id         SERIAL PRIMARY KEY,
    station_id INTEGER NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    name       TEXT    NOT NULL,
    role_name  TEXT,
    group_id   INTEGER REFERENCES ember_schema.member_group (id) ON DELETE CASCADE,
    period     TEXT    NOT NULL DEFAULT 'month',
    rounding   TEXT    NOT NULL DEFAULT 'exact'
);

CREATE INDEX idx_attendance_report_preset_station ON ember_schema.attendance_report_preset (station_id);
