-- Station geolocation: opt-in postal address + WGS-84 coordinates.

ALTER TABLE ember_schema.station
    ADD COLUMN address_line TEXT,
    ADD COLUMN postal_code  TEXT,
    ADD COLUMN city         TEXT,
    ADD COLUMN country      TEXT,
    ADD COLUMN latitude     NUMERIC(9, 6),
    ADD COLUMN longitude    NUMERIC(9, 6);

COMMENT ON COLUMN ember_schema.station.country
    IS 'ISO-3166-1 alpha-2 (e.g. ''DE''). Free-form text.';
COMMENT ON COLUMN ember_schema.station.latitude
    IS 'WGS-84 latitude in degrees, [-90, 90]. NULL if the station has not opted into geolocation.';
COMMENT ON COLUMN ember_schema.station.longitude
    IS 'WGS-84 longitude in degrees, [-180, 180]. NULL if the station has not opted into geolocation.';

CREATE INDEX station_coords_idx
    ON ember_schema.station (latitude, longitude)
    WHERE latitude IS NOT NULL AND longitude IS NOT NULL;

-- Great-circle distance in kilometres via the haversine formula. STRICT so any NULL input
-- yields NULL; IMMUTABLE + PARALLEL SAFE so the planner is free to inline and parallelise.
CREATE OR REPLACE FUNCTION ember_schema.haversine_km(
    lat1 NUMERIC, lon1 NUMERIC,
    lat2 NUMERIC, lon2 NUMERIC
) RETURNS DOUBLE PRECISION
    LANGUAGE SQL
    IMMUTABLE
    STRICT
    PARALLEL SAFE
AS $$
    SELECT 2 * 6371 * asin(sqrt(
        power(sin(radians((lat2 - lat1) / 2)), 2)
        + cos(radians(lat1)) * cos(radians(lat2))
          * power(sin(radians((lon2 - lon1) / 2)), 2)
    ));
$$;

COMMENT ON FUNCTION ember_schema.haversine_km(NUMERIC, NUMERIC, NUMERIC, NUMERIC)
    IS 'Great-circle distance in kilometres between two WGS-84 coordinates. ~0.5% error vs ellipsoid.';

-- Member join date: when the member joined the station. Editable by managers, defaults to the
-- date the membership row was created. Existing rows have no historical create timestamp on
-- station_member, so backfill with the current date as a best-effort baseline.
ALTER TABLE ember_schema.station_member
    ADD COLUMN join_date DATE NOT NULL DEFAULT CURRENT_DATE;

COMMENT ON COLUMN ember_schema.station_member.join_date
    IS 'When the member joined the station. Editable; defaults to today on insert.';

ALTER TABLE ember_schema.page_image RENAME TO page_file;
ALTER TABLE ember_schema.page_file RENAME CONSTRAINT page_image_pkey TO page_file_pkey;
ALTER TABLE ember_schema.page_file RENAME CONSTRAINT page_image_page_id_fkey TO page_file_page_id_fkey;
ALTER SEQUENCE ember_schema.page_image_id_seq RENAME TO page_file_id_seq;

ALTER TABLE ember_schema.page_file
    ADD COLUMN station_id   INTEGER REFERENCES ember_schema.station(id) ON DELETE CASCADE,
    ADD COLUMN content_hash TEXT;

UPDATE ember_schema.page_file pf
    SET station_id = sp.station_id
    FROM ember_schema.station_page sp
    WHERE sp.id = pf.page_id
      AND pf.station_id IS NULL;

ALTER TABLE ember_schema.page_file
    ALTER COLUMN station_id SET NOT NULL,
    ALTER COLUMN page_id    DROP NOT NULL;

ALTER TABLE ember_schema.page_file
    DROP CONSTRAINT page_file_page_id_fkey,
    ADD CONSTRAINT page_file_page_id_fkey
        FOREIGN KEY (page_id) REFERENCES ember_schema.station_page(id) ON DELETE SET NULL;

CREATE UNIQUE INDEX page_file_station_hash_idx
    ON ember_schema.page_file (station_id, content_hash)
    WHERE content_hash IS NOT NULL;

CREATE INDEX page_file_station_idx
    ON ember_schema.page_file (station_id);

CREATE INDEX page_file_page_idx
    ON ember_schema.page_file (page_id)
    WHERE page_id IS NOT NULL;

CREATE INDEX page_file_uploaded_at_idx
    ON ember_schema.page_file (station_id, uploaded_at DESC);

CREATE INDEX page_row_page_idx
    ON ember_schema.page_row (page_id, sort_order);

CREATE INDEX page_cell_row_idx
    ON ember_schema.page_cell (row_id, sort_order);

COMMENT ON COLUMN ember_schema.page_file.station_id IS 'Owning station. Mirrors station_page.station_id; lets the storage layer build paths and enforce per-station dedup without joining.';
COMMENT ON COLUMN ember_schema.page_file.content_hash IS 'Lowercase hex SHA-256 of the file bytes. Combined with station_id forms the on-disk filename.';
COMMENT ON COLUMN ember_schema.page_file.page_id IS 'The page that originally owned this file. Nullable so a row survives the deletion of its owning page when other pages in the same station reference the same file via dedup.';

ALTER TABLE ember_schema.page_file
    ADD COLUMN default_alt_text    TEXT,
    ADD COLUMN default_description TEXT;

COMMENT ON COLUMN ember_schema.page_file.default_alt_text IS 'Default alt text applied to gallery / image cells that pick this file unless the user overrides it.';
COMMENT ON COLUMN ember_schema.page_file.default_description IS 'Default caption / description applied to gallery / image cells that pick this file unless the user overrides it.';

CREATE TABLE ember_schema.page_file_folder (
    id          SERIAL      PRIMARY KEY,
    station_id  INTEGER     NOT NULL REFERENCES ember_schema.station(id) ON DELETE CASCADE,
    parent_id   INTEGER              REFERENCES ember_schema.page_file_folder(id) ON DELETE CASCADE,
    name        TEXT        NOT NULL,
    sort_order  INTEGER     NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX page_file_folder_station_idx ON ember_schema.page_file_folder (station_id, parent_id, sort_order);

CREATE TABLE ember_schema.page_file_tag (
    id         SERIAL  PRIMARY KEY,
    station_id INTEGER NOT NULL REFERENCES ember_schema.station(id) ON DELETE CASCADE,
    name       TEXT    NOT NULL,
    color      TEXT,
    UNIQUE (station_id, name)
);

CREATE INDEX page_file_tag_station_idx ON ember_schema.page_file_tag (station_id);

CREATE TABLE ember_schema.page_file_tag_assignment (
    file_id INTEGER NOT NULL REFERENCES ember_schema.page_file(id) ON DELETE CASCADE,
    tag_id  INTEGER NOT NULL REFERENCES ember_schema.page_file_tag(id) ON DELETE CASCADE,
    PRIMARY KEY (file_id, tag_id)
);

CREATE INDEX page_file_tag_assignment_tag_idx ON ember_schema.page_file_tag_assignment (tag_id);

ALTER TABLE ember_schema.page_file
    ADD COLUMN folder_id INTEGER REFERENCES ember_schema.page_file_folder(id) ON DELETE SET NULL;

CREATE INDEX page_file_folder_membership_idx ON ember_schema.page_file (station_id, folder_id);

COMMENT ON TABLE ember_schema.page_file_folder IS 'Hierarchical folder tree per station for organising page files. Each folder is scoped to one station; parent_id forms the tree (NULL = top level).';
COMMENT ON TABLE ember_schema.page_file_tag IS 'Free-form labels attached to page files. Unique per (station, name) so duplicates inside a single station are not possible.';
COMMENT ON TABLE ember_schema.page_file_tag_assignment IS 'Many-to-many join between page files and tags.';
COMMENT ON COLUMN ember_schema.page_file.folder_id IS 'Optional folder this file lives in. NULL means the file sits at the station-level root.';

-- News: public-facing UUID for enumeration-safe deep-links from public page cells (concept §2.3).
ALTER TABLE ember_schema.news
    ADD COLUMN public_uid UUID NOT NULL UNIQUE DEFAULT gen_random_uuid();

COMMENT ON COLUMN ember_schema.news.public_uid IS 'Stable opaque public identifier. Used by public page cells (e.g. NEWS_TEASER) and any external link that should survive station transfer without renumbering. The integer id stays internal.';

-- station_page + station_event + kb_file: same UUID treatment for the cells that link to them
-- (PAGE_LINK / FEATURED_EVENT / UPCOMING_EVENTS / PAST_EVENT_RECAP / KB_ARTICLE). concept §2.3.
ALTER TABLE ember_schema.station_page
    ADD COLUMN public_uid UUID NOT NULL UNIQUE DEFAULT gen_random_uuid();
ALTER TABLE ember_schema.station_event
    ADD COLUMN public_uid UUID NOT NULL UNIQUE DEFAULT gen_random_uuid();
ALTER TABLE ember_schema.kb_file
    ADD COLUMN public_uid UUID NOT NULL UNIQUE DEFAULT gen_random_uuid();

COMMENT ON COLUMN ember_schema.station_page.public_uid IS 'Stable opaque public identifier referenced from public page cells (PAGE_LINK) and external deep-links.';
COMMENT ON COLUMN ember_schema.station_event.public_uid IS 'Stable opaque public identifier referenced from public page cells (FEATURED_EVENT, UPCOMING_EVENTS, PAST_EVENT_RECAP) and the public event-detail route.';
COMMENT ON COLUMN ember_schema.kb_file.public_uid IS 'Stable opaque public identifier referenced from public page cells (KB_ARTICLE) and external deep-links.';

-- Form purpose discriminates between three sidebar entry points (concept §4.1):
--   INTERNAL - the existing /station/forms admin (members-only forms)
--   CONTACT  - publicly answerable "leave us a message" forms surfaced under /station/pages/forms
--   POLL     - publicly answerable polls surfaced under /station/pages/polls
-- The question-type whitelist (FormQuestionType.allowedFor) is enforced both server-side
-- and in the editor based on this column. Stored as TEXT to match the convention for status
-- enums (see Form.status, News.status, etc.).
ALTER TABLE ember_schema.form
    ADD COLUMN purpose    TEXT NOT NULL DEFAULT 'INTERNAL',
    ADD COLUMN public_uid UUID NOT NULL UNIQUE DEFAULT gen_random_uuid();

COMMENT ON COLUMN ember_schema.form.purpose IS 'Form audience and sidebar entry point. INTERNAL = members-only (legacy /station/forms admin). CONTACT = publicly answerable contact form (/station/pages/forms). POLL = publicly answerable poll (/station/pages/polls). Constrains the allowed FormQuestionType set via FormQuestionType.allowedFor(purpose).';
COMMENT ON COLUMN ember_schema.form.public_uid IS 'Stable opaque public identifier referenced from public page cells (FORMS_CTA, POLL_EMBED) and the public form-response endpoint. The integer id stays internal.';

-- Anonymous public form responses (concept §4.4).
-- Member-id becomes nullable so the public submit endpoint can store responses with no
-- member attached; submitter_hash is the pseudonymous identifier for those rows. Exactly
-- one of (member_id, submitter_hash) is set per row. POLL dedup and CONTACT rate-limit
-- both key off the hash. A partial UNIQUE on (form_id, submitter_hash) is intentionally
-- NOT created at the DB level — dedup only applies to POLL purposes and is enforced in
-- the form service, since the constraint cannot reference form.purpose without a trigger.
ALTER TABLE ember_schema.form_response
    ALTER COLUMN member_id    DROP NOT NULL,
    ALTER COLUMN submitted_by DROP NOT NULL,
    ADD COLUMN submitter_hash BYTEA,
    ADD CONSTRAINT form_response_subject_chk
        CHECK ((member_id IS NOT NULL AND submitted_by IS NOT NULL AND submitter_hash IS NULL)
            OR (member_id IS NULL AND submitted_by IS NULL AND submitter_hash IS NOT NULL));

CREATE INDEX form_response_submitter_hash_idx
    ON ember_schema.form_response (form_id, submitter_hash)
    WHERE submitter_hash IS NOT NULL;

COMMENT ON COLUMN ember_schema.form_response.member_id IS 'The member this response belongs to. NULL for anonymous public submissions (CONTACT / POLL purposes via the public submit endpoint); in that case submitter_hash carries the pseudonymous identifier instead.';
COMMENT ON COLUMN ember_schema.form_response.submitter_hash IS 'SHA-256 of (real client IP || form id || instance salt) for anonymous public submissions. Pseudonymous — non-reversible without the salt. Used for poll dedup (single response per visitor per poll) and contact-form rate-limiting; never persisted as a raw IP. NULL for member responses. The salt is stored as Base64 in application_setting under the key ''form_response.submitter_hash_salt'', generated on first use.';

ALTER TABLE ember_schema.quiz_catalog ADD COLUMN public_render BOOLEAN NOT NULL DEFAULT FALSE;
COMMENT ON COLUMN ember_schema.quiz_catalog.public_render IS 'When true, the catalog''s questions are eligible for the public quiz random endpoint that backs the QUIZ_TEASER cell. Defaults to FALSE; catalog managers opt-in per catalog from the catalog settings view.';

UPDATE ember_schema.page_cell
SET content_type = 'BLOG_SIGNUP'
WHERE content_type = 'NEWSLETTER_SIGNUP';

ALTER TABLE ember_schema.form_response
    ADD COLUMN acknowledged_at TIMESTAMPTZ,
    ADD COLUMN acknowledged_by INTEGER REFERENCES ember_schema.station_member (id) ON DELETE SET NULL;

COMMENT ON COLUMN ember_schema.form_response.acknowledged_at IS 'When a CONTACT-form submission was first marked as read/handled by a member with PAGE_FORMS_VIEW. NULL until acknowledged. POLL submissions are never acknowledged (analytics-only).';
COMMENT ON COLUMN ember_schema.form_response.acknowledged_by IS 'Member who acknowledged a CONTACT-form submission. Set together with acknowledged_at; FK retains the history even after the acknowledger leaves the station (SET NULL on delete).';

UPDATE ember_schema.page_cell
SET content_type = 'MEMBER_LIST_SPOTLIGHT'
WHERE content_type = 'OFFICERS_ROW';
