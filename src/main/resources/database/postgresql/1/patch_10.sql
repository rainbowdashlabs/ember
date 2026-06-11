-- Public Pages: Layout editor for station public pages

-- Page belonging to a station
CREATE TABLE ember_schema.station_page (
    id               SERIAL PRIMARY KEY,
    station_id       INTEGER     NOT NULL REFERENCES ember_schema.station(id) ON DELETE CASCADE,
    parent_id        INTEGER              REFERENCES ember_schema.station_page(id) ON DELETE CASCADE,
    title            TEXT        NOT NULL,
    slug             TEXT        NOT NULL,
    published        BOOLEAN     NOT NULL DEFAULT FALSE,
    sort_order       INTEGER     NOT NULL DEFAULT 0,
    meta_description TEXT,
    og_image_id      INTEGER,
    created_by       INTEGER     NOT NULL REFERENCES ember_schema.station_member(id) ON DELETE SET NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (station_id, slug)
);

-- Horizontal row within a page
CREATE TABLE ember_schema.page_row (
    id         SERIAL  PRIMARY KEY,
    page_id    INTEGER NOT NULL REFERENCES ember_schema.station_page(id) ON DELETE CASCADE,
    sort_order INTEGER NOT NULL DEFAULT 0
);

-- Column (cell) within a row
CREATE TABLE ember_schema.page_cell (
    id            SERIAL  PRIMARY KEY,
    row_id        INTEGER NOT NULL REFERENCES ember_schema.page_row(id) ON DELETE CASCADE,
    sort_order    INTEGER NOT NULL DEFAULT 0,
    width_percent NUMERIC NOT NULL DEFAULT 100,
    content_type  TEXT    NOT NULL,
    content       TEXT    NOT NULL DEFAULT '',
    config        JSONB   NOT NULL DEFAULT '{}'
);

-- Image uploaded for a page (stored on filesystem at data/page-images/{pageId}/{imageId})
CREATE TABLE ember_schema.page_image (
    id          SERIAL      PRIMARY KEY,
    page_id     INTEGER     NOT NULL REFERENCES ember_schema.station_page(id) ON DELETE CASCADE,
    file_name   TEXT        NOT NULL,
    mime_type   TEXT        NOT NULL,
    file_size   BIGINT      NOT NULL,
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- og_image FK (deferred because page_image references station_page)
ALTER TABLE ember_schema.station_page
    ADD CONSTRAINT fk_station_page_og_image
    FOREIGN KEY (og_image_id) REFERENCES ember_schema.page_image(id) ON DELETE SET NULL;

-- Station landing page, public pages toggle, and public slug
ALTER TABLE ember_schema.station
    ADD COLUMN landing_page_id      INTEGER REFERENCES ember_schema.station_page(id) ON DELETE SET NULL,
    ADD COLUMN public_pages_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN public_slug          TEXT UNIQUE;

-- Backfill public slugs for existing stations from their name
UPDATE ember_schema.station
SET public_slug = lower(
    regexp_replace(
        regexp_replace(
            translate(name, 'ÄäÖöÜüß', 'aaoouu-'),
            '[^a-zA-Z0-9]+', '-', 'g'),
        '^-+|-+$', '', 'g'))
WHERE public_slug IS NULL;

-- Deduplicate any collisions by appending the station id
UPDATE ember_schema.station s
SET public_slug = s.public_slug || '-' || s.id
WHERE EXISTS (
    SELECT 1 FROM ember_schema.station s2
    WHERE s2.public_slug = s.public_slug AND s2.id < s.id
);

-- Normalize station_application status values to uppercase for enum mapping
UPDATE ember_schema.station_application SET status = upper(status) WHERE status != upper(status);
ALTER TABLE ember_schema.station_application ALTER COLUMN status SET DEFAULT 'UNVERIFIED';

-- Public waitlist: allow stations to expose waitlists for self-registration
ALTER TABLE ember_schema.waiting_list ADD COLUMN public BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE ember_schema.waiting_list_field ADD COLUMN public BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE ember_schema.station ADD COLUMN public_waitlist_enabled BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE ember_schema.waitlist_verification_token (
    id           SERIAL      PRIMARY KEY,
    token        TEXT        NOT NULL UNIQUE,
    list_id      INTEGER     NOT NULL REFERENCES ember_schema.waiting_list (id) ON DELETE CASCADE,
    firstname    TEXT        NOT NULL,
    lastname     TEXT        NOT NULL DEFAULT '',
    email        TEXT        NOT NULL,
    guardians    JSONB       NOT NULL DEFAULT '[]'::jsonb,
    field_values JSONB       NOT NULL DEFAULT '{}'::jsonb,
    notes        TEXT        NOT NULL DEFAULT '',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at   TIMESTAMPTZ NOT NULL DEFAULT (now() + interval '24 hours')
);

CREATE INDEX idx_wl_verify_token ON ember_schema.waitlist_verification_token (token);
CREATE INDEX idx_wl_verify_expires ON ember_schema.waitlist_verification_token (expires_at);

-- Public blog: allow news entries to be published on the public station page
ALTER TABLE ember_schema.news ADD COLUMN public_blog BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE ember_schema.station ADD COLUMN public_blog_enabled BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_news_public_blog ON ember_schema.news (station_id, public_blog) WHERE public_blog = TRUE AND published_at IS NOT NULL;

-- Guardian name split: replace single name column with firstname + lastname
ALTER TABLE ember_schema.waiting_list_entry_guardian ADD COLUMN firstname TEXT NOT NULL DEFAULT '';
ALTER TABLE ember_schema.waiting_list_entry_guardian ADD COLUMN lastname TEXT NOT NULL DEFAULT '';

-- Migrate: split existing name into firstname (first word) and lastname (rest)
UPDATE ember_schema.waiting_list_entry_guardian
SET firstname = split_part(name, ' ', 1),
    lastname  = CASE
        WHEN position(' ' IN name) > 0 THEN substring(name FROM position(' ' IN name) + 1)
        ELSE ''
    END
WHERE name != '';

ALTER TABLE ember_schema.waiting_list_entry_guardian DROP COLUMN name;
