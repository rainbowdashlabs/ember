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

-- Storage monitoring & quota system

-- Per-station storage usage tracking by category
CREATE TABLE ember_schema.station_storage_usage (
    station_id   INTEGER NOT NULL REFERENCES ember_schema.station(id) ON DELETE CASCADE,
    category     TEXT    NOT NULL,
    total_bytes  BIGINT  NOT NULL DEFAULT 0,
    file_count   INTEGER NOT NULL DEFAULT 0,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (station_id, category)
);

COMMENT ON TABLE ember_schema.station_storage_usage IS 'Tracks per-station storage usage grouped by category';
COMMENT ON COLUMN ember_schema.station_storage_usage.category IS 'Storage category enum value (KB_FILES, BOARD_ATTACHMENTS, PAGE_IMAGES, AVATARS, IMAGES)';
COMMENT ON COLUMN ember_schema.station_storage_usage.total_bytes IS 'Total bytes used in this category for this station';
COMMENT ON COLUMN ember_schema.station_storage_usage.file_count IS 'Number of files in this category for this station';
COMMENT ON COLUMN ember_schema.station_storage_usage.updated_at IS 'When this usage record was last updated';

-- Reusable quota presets for easy station configuration
CREATE TABLE ember_schema.storage_quota_preset (
    id          SERIAL  PRIMARY KEY,
    name        TEXT    NOT NULL UNIQUE,
    total       BIGINT  NOT NULL,
    kb          BIGINT  NOT NULL,
    board       BIGINT  NOT NULL,
    images      BIGINT  NOT NULL,
    pages       BIGINT  NOT NULL,
    per_file    BIGINT  NOT NULL,
    per_image   BIGINT  NOT NULL
);

COMMENT ON TABLE ember_schema.storage_quota_preset IS 'Named quota profiles that can be applied to stations';
COMMENT ON COLUMN ember_schema.storage_quota_preset.name IS 'Display name of the preset (e.g. Small, Standard, Premium)';
COMMENT ON COLUMN ember_schema.storage_quota_preset.total IS 'Total storage quota in bytes';
COMMENT ON COLUMN ember_schema.storage_quota_preset.kb IS 'KB files quota in bytes';
COMMENT ON COLUMN ember_schema.storage_quota_preset.board IS 'Board attachments quota in bytes';
COMMENT ON COLUMN ember_schema.storage_quota_preset.images IS 'Images quota in bytes';
COMMENT ON COLUMN ember_schema.storage_quota_preset.pages IS 'Page images quota in bytes';
COMMENT ON COLUMN ember_schema.storage_quota_preset.per_file IS 'Maximum bytes per single file upload';
COMMENT ON COLUMN ember_schema.storage_quota_preset.per_image IS 'Maximum bytes per single image upload';

-- Per-station quota overrides (NULL = use instance default from config)
ALTER TABLE ember_schema.station
    ADD COLUMN storage_quota_bytes        BIGINT,
    ADD COLUMN storage_quota_kb_bytes     BIGINT,
    ADD COLUMN storage_quota_board_bytes  BIGINT,
    ADD COLUMN storage_quota_images_bytes BIGINT,
    ADD COLUMN storage_quota_pages_bytes  BIGINT,
    ADD COLUMN storage_per_file_bytes     BIGINT,
    ADD COLUMN storage_per_image_bytes    BIGINT,
    ADD COLUMN storage_warning_sent       BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN storage_preset_id         INTEGER REFERENCES ember_schema.storage_quota_preset(id) ON DELETE SET NULL;

COMMENT ON COLUMN ember_schema.station.storage_quota_bytes IS 'Total storage quota override in bytes (NULL = use instance default)';
COMMENT ON COLUMN ember_schema.station.storage_quota_kb_bytes IS 'KB files quota override in bytes (NULL = use instance default)';
COMMENT ON COLUMN ember_schema.station.storage_quota_board_bytes IS 'Board attachments quota override in bytes (NULL = use instance default)';
COMMENT ON COLUMN ember_schema.station.storage_quota_images_bytes IS 'Images quota override in bytes (NULL = use instance default)';
COMMENT ON COLUMN ember_schema.station.storage_quota_pages_bytes IS 'Page images quota override in bytes (NULL = use instance default)';
COMMENT ON COLUMN ember_schema.station.storage_per_file_bytes IS 'Maximum bytes per file upload override (NULL = use instance default)';
COMMENT ON COLUMN ember_schema.station.storage_per_image_bytes IS 'Maximum bytes per image upload override (NULL = use instance default)';
COMMENT ON COLUMN ember_schema.station.storage_warning_sent IS 'Whether the storage warning notification has been sent for this station';
COMMENT ON COLUMN ember_schema.station.storage_preset_id IS 'Last applied storage quota preset (NULL if using defaults or manually configured)';

-- ---------------------------------------------------------------------------
-- Federated uploader identity for board ticket attachments
--
-- Federated board members from another station can attach files, but the
-- previous schema referenced the local station_member table by id. Replace
-- the local FK with the (station_uid, member_uid) pair used by every other
-- federated board column (board_ticket.creator_*, board_ticket_comment.author_*,
-- board_ticket_transition.actor_*, board_ticket_watcher.watcher_*).
-- ---------------------------------------------------------------------------
ALTER TABLE ember_schema.board_ticket_attachment
    ADD COLUMN uploader_station_uid UUID,
    ADD COLUMN uploader_member_uid  UUID;

-- Backfill from the existing local FK so historical rows keep their author.
UPDATE ember_schema.board_ticket_attachment a
SET uploader_station_uid = s.uid,
    uploader_member_uid  = sm.uid
FROM ember_schema.station_member sm
         JOIN ember_schema.station s ON s.id = sm.station_id
WHERE sm.id = a.uploaded_by;

ALTER TABLE ember_schema.board_ticket_attachment
    ALTER COLUMN uploader_station_uid SET NOT NULL,
    ALTER COLUMN uploader_member_uid  SET NOT NULL,
    DROP COLUMN uploaded_by;

COMMENT ON COLUMN ember_schema.board_ticket_attachment.uploader_station_uid
    IS 'UUID of the station the uploader belongs to. Together with uploader_member_uid this identifies the author, including federated members from other stations.';
COMMENT ON COLUMN ember_schema.board_ticket_attachment.uploader_member_uid
    IS 'UUID of the uploading member within their station. Federation-safe author identity.';

-- ---------------------------------------------------------------------------
-- Missing FKs on quiz_test_attempt
--
-- member_id and graded_by were declared as plain INT columns. Without FKs to
-- station_member, deleting a member leaves orphan rows with dangling integer
-- ids — a GDPR/data-integrity problem.
--   member_id  → CASCADE: the member's attempts are part of their personal data
--   graded_by  → SET NULL: the attempt is preserved but the grader reference
--                is severed (graders are reviewers, not subjects)
-- ---------------------------------------------------------------------------

-- Clean up any pre-existing orphans before we add the constraints (defensive).
UPDATE ember_schema.quiz_test_attempt
SET graded_by = NULL
WHERE graded_by IS NOT NULL
  AND graded_by NOT IN (SELECT id FROM ember_schema.station_member);

DELETE FROM ember_schema.quiz_test_attempt
WHERE member_id NOT IN (SELECT id FROM ember_schema.station_member);

ALTER TABLE ember_schema.quiz_test_attempt
    ADD CONSTRAINT fk_quiz_test_attempt_member
        FOREIGN KEY (member_id) REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_quiz_test_attempt_grader
        FOREIGN KEY (graded_by) REFERENCES ember_schema.station_member (id) ON DELETE SET NULL;
