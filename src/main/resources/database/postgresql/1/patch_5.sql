-- Public calendar
ALTER TABLE ember_schema.station ADD COLUMN public_calendar_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE ember_schema.event_category ADD COLUMN public BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE ember_schema.station_event ADD COLUMN public BOOLEAN DEFAULT NULL;
ALTER TABLE ember_schema.station_event ADD COLUMN registration_limit INTEGER;
ALTER TABLE ember_schema.station_event ADD COLUMN deadline_notified BOOLEAN NOT NULL DEFAULT FALSE;
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
    attendance_template_id        INTEGER REFERENCES ember_schema.attendance_template (id) ON DELETE SET NULL,
    registration_limit            INTEGER,
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

-- Federated event sharing
CREATE TABLE ember_schema.event_federation_share
(
    id       SERIAL PRIMARY KEY,
    event_id INTEGER NOT NULL REFERENCES ember_schema.station_event (id) ON DELETE CASCADE,
    scope    TEXT    NOT NULL DEFAULT 'ALL_PARTNERS',
    UNIQUE (event_id)
);

CREATE TABLE ember_schema.event_federation_share_target
(
    share_id   INTEGER NOT NULL REFERENCES ember_schema.event_federation_share (id) ON DELETE CASCADE,
    partner_id INTEGER NOT NULL REFERENCES ember_schema.federation_partner (id) ON DELETE CASCADE,
    PRIMARY KEY (share_id, partner_id)
);

CREATE TABLE ember_schema.event_federation_registration
(
    id               SERIAL PRIMARY KEY,
    event_id         INTEGER     NOT NULL REFERENCES ember_schema.station_event (id) ON DELETE CASCADE,
    partner_id       INTEGER     NOT NULL REFERENCES ember_schema.federation_partner (id) ON DELETE CASCADE,
    remote_member_id TEXT        NOT NULL,
    event_date       DATE        NOT NULL,
    status           TEXT        NOT NULL DEFAULT 'PENDING',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (event_id, partner_id, remote_member_id, event_date)
);

CREATE TABLE ember_schema.federation_member_name_cache
(
    partner_id       INTEGER     NOT NULL REFERENCES ember_schema.federation_partner (id) ON DELETE CASCADE,
    remote_member_id TEXT        NOT NULL,
    display_name     TEXT        NOT NULL,
    cached_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (partner_id, remote_member_id)
);

-- Comments: events
CREATE TABLE ember_schema.event_comment
(
    id         SERIAL PRIMARY KEY,
    event_id   INTEGER     NOT NULL REFERENCES ember_schema.station_event (id) ON DELETE CASCADE,
    parent_id  INTEGER              REFERENCES ember_schema.event_comment (id) ON DELETE SET NULL,
    author_id  INTEGER     NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    content    TEXT        NOT NULL,
    deleted    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ
);
CREATE INDEX idx_event_comment_event ON ember_schema.event_comment (event_id);

-- Comments: news (replace existing news_comment table structure)
ALTER TABLE ember_schema.news_comment ADD COLUMN updated_at TIMESTAMPTZ;
ALTER TABLE ember_schema.news_comment ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE ember_schema.news_comment DROP CONSTRAINT IF EXISTS news_comment_parent_id_fkey;
ALTER TABLE ember_schema.news_comment ADD FOREIGN KEY (parent_id) REFERENCES ember_schema.news_comment (id) ON DELETE SET NULL;

-- Entity notes (shared markdown document per entity)
CREATE TABLE ember_schema.entity_note
(
    id          SERIAL PRIMARY KEY,
    entity_type TEXT        NOT NULL,
    entity_id   INTEGER     NOT NULL,
    station_id  INTEGER     NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    content     TEXT        NOT NULL DEFAULT '',
    updated_by  INTEGER              REFERENCES ember_schema.station_member (id) ON DELETE SET NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (entity_type, entity_id)
);

-- Note version history (diff-based)
CREATE TABLE ember_schema.entity_note_version
(
    id         SERIAL PRIMARY KEY,
    note_id    INTEGER     NOT NULL REFERENCES ember_schema.entity_note (id) ON DELETE CASCADE,
    diff_patch TEXT        NOT NULL,
    author_id  INTEGER     NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_entity_note_version_note ON ember_schema.entity_note_version (note_id);

-- User feed tokens (shared for iCal + RSS/Atom feeds)
CREATE TABLE ember_schema.user_feed_token
(
    member_id               INTEGER     PRIMARY KEY REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    token                   TEXT        NOT NULL UNIQUE,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    ical_polled_at          TIMESTAMPTZ,
    notification_polled_at  TIMESTAMPTZ
);

-- News acknowledgements (track which members have read a news article)
CREATE TABLE ember_schema.news_acknowledgement
(
    news_id    INTEGER     NOT NULL REFERENCES ember_schema.news (id) ON DELETE CASCADE,
    member_id  INTEGER     NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (news_id, member_id)
);
CREATE INDEX idx_news_acknowledgement_member ON ember_schema.news_acknowledgement (member_id);

-- Knowledge base favourites
CREATE TABLE ember_schema.kb_favourite
(
    member_id  INTEGER     NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    file_id    INTEGER     NOT NULL REFERENCES ember_schema.kb_file (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (member_id, file_id)
);

-- User problem reports
CREATE TABLE ember_schema.problem_report
(
    id              SERIAL PRIMARY KEY,
    station_id      INTEGER     NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    member_id       INTEGER              REFERENCES ember_schema.station_member (id) ON DELETE SET NULL,
    reporter_name   TEXT        NOT NULL,
    message         TEXT        NOT NULL,
    page_url        TEXT,
    user_roles      TEXT,
    recent_requests JSONB,
    browser_info    TEXT,
    screen_size     TEXT,
    acknowledged    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
