-- Event field defaults for attendance template fields
-- source: 'VALUE' for static value, or an event property name like 'EVENT_NAME', 'EVENT_START_TIME', etc.
CREATE TABLE ember_schema.event_field_default
(
    event_id INTEGER NOT NULL REFERENCES ember_schema.station_event (id) ON DELETE CASCADE,
    field_id INTEGER NOT NULL REFERENCES ember_schema.attendance_template_field (id) ON DELETE CASCADE,
    source   TEXT    NOT NULL, -- VALUE, EVENT_NAME, EVENT_DESCRIPTION, EVENT_START_TIME, EVENT_END_TIME, EVENT_DATE
    value    TEXT,             -- only used when source = 'VALUE'
    PRIMARY KEY (event_id, field_id)
);
