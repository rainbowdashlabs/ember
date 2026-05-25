-- Public calendar
ALTER TABLE ember_schema.station ADD COLUMN public_calendar_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE ember_schema.event_category ADD COLUMN public BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE ember_schema.station_event ADD COLUMN public BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE ember_schema.event_field ADD COLUMN public BOOLEAN NOT NULL DEFAULT FALSE;
