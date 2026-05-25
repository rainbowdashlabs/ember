-- Public calendar
ALTER TABLE ember_schema.station ADD COLUMN public_calendar_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE ember_schema.event_category ADD COLUMN public BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE ember_schema.station_event ADD COLUMN public BOOLEAN DEFAULT NULL;
ALTER TABLE ember_schema.event_field ADD COLUMN public BOOLEAN NOT NULL DEFAULT FALSE;

-- Theme feel (ROUNDED/CORNERS)
ALTER TABLE ember_schema.station ADD COLUMN default_feel TEXT NOT NULL DEFAULT 'ROUNDED';
ALTER TABLE ember_schema.station ADD COLUMN allow_user_feel BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE ember_schema.user_settings ADD COLUMN feel TEXT NOT NULL DEFAULT 'ROUNDED';

-- Feed enabled preference per notification type
ALTER TABLE ember_schema.user_notification_settings ADD COLUMN feed_enabled BOOLEAN NOT NULL DEFAULT TRUE;

-- Event templates
CREATE TABLE ember_schema.event_template
(
    id                            SERIAL PRIMARY KEY,
    station_id                    INTEGER NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    name                          TEXT    NOT NULL,
    title                         TEXT,
    description                   TEXT,
    category_id                   INTEGER REFERENCES ember_schema.event_category (id) ON DELETE SET NULL,
    event_type                    TEXT,
    requires_registration         BOOLEAN,
    registration_deadline_offset  INTERVAL,
    requires_confirmation         BOOLEAN,
    restriction_mode              TEXT,
    UNIQUE (station_id, name)
);

CREATE TABLE ember_schema.event_template_field
(
    id                  SERIAL PRIMARY KEY,
    template_id         INTEGER NOT NULL REFERENCES ember_schema.event_template (id) ON DELETE CASCADE,
    name                TEXT    NOT NULL,
    field_type          TEXT    NOT NULL DEFAULT 'string',
    config              JSONB,
    position            INTEGER NOT NULL DEFAULT 0,
    overview            BOOLEAN NOT NULL DEFAULT FALSE,
    public              BOOLEAN NOT NULL DEFAULT FALSE,
    attendance_field_id INTEGER REFERENCES ember_schema.attendance_template_field (id) ON DELETE SET NULL
);

CREATE TABLE ember_schema.event_template_restriction
(
    template_id INTEGER NOT NULL REFERENCES ember_schema.event_template (id) ON DELETE CASCADE,
    role_id     INTEGER NOT NULL,
    PRIMARY KEY (template_id, role_id)
);

-- User feed tokens (shared for iCal + RSS/Atom feeds)
CREATE TABLE ember_schema.user_feed_token
(
    member_id  INTEGER     PRIMARY KEY REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    token      TEXT        NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
