-- Public calendar
ALTER TABLE ember_schema.station ADD COLUMN public_calendar_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE ember_schema.event_category ADD COLUMN public BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE ember_schema.station_event ADD COLUMN public BOOLEAN DEFAULT NULL;
ALTER TABLE ember_schema.event_field ADD COLUMN public BOOLEAN NOT NULL DEFAULT FALSE;

-- Theme feel (ROUNDED/CORNERS)
ALTER TABLE ember_schema.station ADD COLUMN default_feel TEXT NOT NULL DEFAULT 'ROUNDED';
ALTER TABLE ember_schema.station ADD COLUMN allow_user_feel BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE ember_schema.user_settings ADD COLUMN feel TEXT NOT NULL DEFAULT 'ROUNDED';
