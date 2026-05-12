-- Add event link to attendance sessions and DECLINED registration status

ALTER TABLE ember_schema.attendance_session
    ADD COLUMN event_id INTEGER REFERENCES ember_schema.station_event (id) ON DELETE SET NULL;
