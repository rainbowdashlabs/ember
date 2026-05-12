-- Convert event start_time/end_time from TIME to TIMESTAMP WITH TIME ZONE, combining with event_date.
-- Drop event_date since start/end timestamps now carry the date.
ALTER TABLE ember_schema.station_event
    ALTER COLUMN start_time TYPE TIMESTAMP WITH TIME ZONE USING (COALESCE(event_date, CURRENT_DATE) + start_time)::TIMESTAMP WITH TIME ZONE,
    ALTER COLUMN end_time TYPE TIMESTAMP WITH TIME ZONE USING (COALESCE(event_date, CURRENT_DATE) + end_time)::TIMESTAMP WITH TIME ZONE;

ALTER TABLE ember_schema.station_event
    DROP COLUMN event_date;
