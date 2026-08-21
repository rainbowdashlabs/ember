-- The station media library, and blocks that belong to a container rather than to a page.
--
-- Two halves of one change, so they land together.
--
-- The first: the file library the page editor built is not a page feature that other features may
-- borrow, it is a media library for the whole station, and it is renamed here to say so. A table
-- rename in PostgreSQL carries indexes, constraints and sequences along, so the objects are renamed
-- with it to keep the schema readable for anyone looking at it through pg_catalog. The patch also
-- records who brought a file in, and lets a news entry hand a file over.
--
-- The second: rows and cells hung off a page, which is why the page editor could only ever build a
-- page. A container owns them instead, and a page, a news entry and a knowledge-base article each
-- own one. Nothing about the editor changes; what changes is what may be edited with it.

ALTER TABLE ember_schema.page_file RENAME TO station_file;
ALTER TABLE ember_schema.page_file_folder RENAME TO station_file_folder;
ALTER TABLE ember_schema.page_file_tag RENAME TO station_file_tag;
ALTER TABLE ember_schema.page_file_tag_assignment RENAME TO station_file_tag_assignment;

ALTER TABLE ember_schema.station_file RENAME CONSTRAINT page_file_pkey TO station_file_pkey;
ALTER TABLE ember_schema.station_file RENAME CONSTRAINT page_file_page_id_fkey TO station_file_page_id_fkey;
ALTER TABLE ember_schema.station_file RENAME CONSTRAINT page_file_station_id_fkey TO station_file_station_id_fkey;
ALTER TABLE ember_schema.station_file RENAME CONSTRAINT page_file_folder_id_fkey TO station_file_folder_id_fkey;
ALTER SEQUENCE ember_schema.page_file_id_seq RENAME TO station_file_id_seq;

ALTER TABLE ember_schema.station_file_folder RENAME CONSTRAINT page_file_folder_pkey TO station_file_folder_pkey;
ALTER TABLE ember_schema.station_file_folder
    RENAME CONSTRAINT page_file_folder_station_id_fkey TO station_file_folder_station_id_fkey;
ALTER TABLE ember_schema.station_file_folder
    RENAME CONSTRAINT page_file_folder_parent_id_fkey TO station_file_folder_parent_id_fkey;
ALTER SEQUENCE ember_schema.page_file_folder_id_seq RENAME TO station_file_folder_id_seq;

ALTER TABLE ember_schema.station_file_tag RENAME CONSTRAINT page_file_tag_pkey TO station_file_tag_pkey;
ALTER TABLE ember_schema.station_file_tag
    RENAME CONSTRAINT page_file_tag_station_id_fkey TO station_file_tag_station_id_fkey;
ALTER TABLE ember_schema.station_file_tag
    RENAME CONSTRAINT page_file_tag_station_id_name_key TO station_file_tag_station_id_name_key;
ALTER SEQUENCE ember_schema.page_file_tag_id_seq RENAME TO station_file_tag_id_seq;

ALTER TABLE ember_schema.station_file_tag_assignment
    RENAME CONSTRAINT page_file_tag_assignment_pkey TO station_file_tag_assignment_pkey;
ALTER TABLE ember_schema.station_file_tag_assignment
    RENAME CONSTRAINT page_file_tag_assignment_file_id_fkey TO station_file_tag_assignment_file_id_fkey;
ALTER TABLE ember_schema.station_file_tag_assignment
    RENAME CONSTRAINT page_file_tag_assignment_tag_id_fkey TO station_file_tag_assignment_tag_id_fkey;

ALTER INDEX IF EXISTS ember_schema.page_file_station_hash_idx RENAME TO station_file_station_hash_idx;
ALTER INDEX IF EXISTS ember_schema.page_file_station_idx RENAME TO station_file_station_idx;
ALTER INDEX IF EXISTS ember_schema.page_file_page_idx RENAME TO station_file_page_idx;
ALTER INDEX IF EXISTS ember_schema.page_file_uploaded_at_idx RENAME TO station_file_uploaded_at_idx;
ALTER INDEX IF EXISTS ember_schema.page_file_folder_membership_idx RENAME TO station_file_folder_membership_idx;
ALTER INDEX IF EXISTS ember_schema.page_file_folder_station_idx RENAME TO station_file_folder_station_idx;
ALTER INDEX IF EXISTS ember_schema.page_file_tag_station_idx RENAME TO station_file_tag_station_idx;
ALTER INDEX IF EXISTS ember_schema.page_file_tag_assignment_tag_idx RENAME TO station_file_tag_assignment_tag_idx;

COMMENT ON TABLE ember_schema.station_file
    IS 'The media library of a station. Every file the station has uploaded for any of its content lives here once, keyed by the hash of its bytes.';
COMMENT ON COLUMN ember_schema.station_file.page_id
    IS 'The page a file was first uploaded for, kept for the history it records. Nullable, because most files are not brought in by a page at all.';
COMMENT ON TABLE ember_schema.station_file_folder
    IS 'Hierarchical folder tree per station for organising media. Each folder is scoped to one station; parent_id forms the tree (NULL = top level).';
COMMENT ON TABLE ember_schema.station_file_tag
    IS 'Free-form labels attached to media files. Unique per (station, name) so duplicates inside a single station are not possible.';
COMMENT ON TABLE ember_schema.station_file_tag_assignment
    IS 'Many-to-many join between media files and tags.';

-- Who brought a file in.
--
-- Uploads are deduplicated per station by the hash of their bytes, so the second member to upload
-- the same picture is handed the row the first one created. A single uploader column would then
-- hand them somebody else's file and they would not see what they just uploaded. Ownership is a
-- set for that reason: a dedup hit adds a row here rather than creating a file.
CREATE TABLE IF NOT EXISTS ember_schema.station_file_uploader
(
    file_id     INTEGER     NOT NULL REFERENCES ember_schema.station_file (id) ON DELETE CASCADE,
    member_id   INTEGER     NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (file_id, member_id)
);

CREATE INDEX IF NOT EXISTS station_file_uploader_member_idx
    ON ember_schema.station_file_uploader (member_id, uploaded_at DESC);

COMMENT ON TABLE ember_schema.station_file_uploader
    IS 'The members who uploaded a file. A set rather than a column, because identical bytes are stored once and may be brought in by several people.';
COMMENT ON COLUMN ember_schema.station_file_uploader.uploaded_at
    IS 'When this member uploaded the file. The earliest row is who first brought it into the station.';

-- What a news entry hands over.
--
-- An attachment is a reference to a media file, never a file of its own, so it inherits the
-- deduplication, the quota and the ownership of the library. RESTRICT on the file is the safety
-- story: an attached file cannot be deleted out from under the entry that hands it out.
CREATE TABLE IF NOT EXISTS ember_schema.news_attachment
(
    id         SERIAL PRIMARY KEY,
    news_id    INTEGER     NOT NULL REFERENCES ember_schema.news (id) ON DELETE CASCADE,
    file_id    INTEGER     NOT NULL REFERENCES ember_schema.station_file (id) ON DELETE RESTRICT,
    label      TEXT,
    sort_order INTEGER     NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS news_attachment_news_idx ON ember_schema.news_attachment (news_id, sort_order);
CREATE INDEX IF NOT EXISTS news_attachment_file_idx ON ember_schema.news_attachment (file_id);

COMMENT ON TABLE ember_schema.news_attachment
    IS 'A file a news entry hands over, pointing at the station media library rather than holding bytes of its own.';
COMMENT ON COLUMN ember_schema.news_attachment.label
    IS 'What a reader sees instead of the file name, for the cases where the file name is not what should be read.';
COMMENT ON COLUMN ember_schema.news_attachment.sort_order
    IS 'The order the author put the attachments in, which is the order they are handed out and travel in.';

CREATE TABLE IF NOT EXISTS ember_schema.content_container
(
    id         SERIAL PRIMARY KEY,
    station_id INTEGER REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS content_container_station_idx ON ember_schema.content_container (station_id);

COMMENT ON TABLE ember_schema.content_container
    IS 'The rows and cells of one piece of authored content. A page, a news entry or a knowledge-base article owns one; the container itself knows nothing about which.';
COMMENT ON COLUMN ember_schema.content_container.station_id
    IS 'The station whose content this belongs to. Null for content the instance owns, which is a system news entry built from blocks: it is read in every station, so it can belong to none of them.';

ALTER TABLE ember_schema.station_page
    ADD COLUMN container_id INTEGER REFERENCES ember_schema.content_container (id) ON DELETE SET NULL;
ALTER TABLE ember_schema.news
    ADD COLUMN container_id INTEGER REFERENCES ember_schema.content_container (id) ON DELETE SET NULL;
ALTER TABLE ember_schema.kb_file
    ADD COLUMN container_id INTEGER REFERENCES ember_schema.content_container (id) ON DELETE SET NULL;

COMMENT ON COLUMN ember_schema.station_page.container_id
    IS 'The blocks this page is built from.';
COMMENT ON COLUMN ember_schema.news.container_id
    IS 'The blocks a rich entry is built from. NULL for an entry written as plain text.';
COMMENT ON COLUMN ember_schema.kb_file.container_id
    IS 'The blocks a rich article is built from. NULL for an article written as plain text.';

-- How an entry was written. Everything that exists was written as text, so the upgrade changes no
-- behaviour: only what the author chooses afterwards does.
ALTER TABLE ember_schema.news
    ADD COLUMN content_mode TEXT NOT NULL DEFAULT 'SIMPLE';
ALTER TABLE ember_schema.kb_file
    ADD COLUMN content_mode TEXT NOT NULL DEFAULT 'SIMPLE';

COMMENT ON COLUMN ember_schema.news.content_mode
    IS 'SIMPLE for an entry written as text, RICH for one built from blocks. The stored text of a rich entry is a projection of its blocks and is never edited directly.';
COMMENT ON COLUMN ember_schema.kb_file.content_mode
    IS 'SIMPLE for an article written as text, RICH for one built from blocks. The stored text of a rich article is a projection of its blocks and is never edited directly.';

-- Repoint the rows. Every page gets a container carrying its own rows, which is what it had before
-- said in another way.
ALTER TABLE ember_schema.page_row
    ADD COLUMN container_id INTEGER REFERENCES ember_schema.content_container (id) ON DELETE CASCADE;

ALTER TABLE ember_schema.content_container
    ADD COLUMN migrated_page_id INTEGER;

INSERT INTO ember_schema.content_container (station_id, migrated_page_id)
SELECT station_id, id
FROM ember_schema.station_page;

UPDATE ember_schema.station_page p
SET container_id = c.id
FROM ember_schema.content_container c
WHERE c.migrated_page_id = p.id;

UPDATE ember_schema.page_row r
SET container_id = p.container_id
FROM ember_schema.station_page p
WHERE p.id = r.page_id;

ALTER TABLE ember_schema.content_container
    DROP COLUMN migrated_page_id;

DELETE FROM ember_schema.page_row WHERE container_id IS NULL;

ALTER TABLE ember_schema.page_row
    ALTER COLUMN container_id SET NOT NULL;
ALTER TABLE ember_schema.page_row
    DROP COLUMN page_id;

CREATE INDEX IF NOT EXISTS page_row_container_idx ON ember_schema.page_row (container_id, sort_order);

COMMENT ON COLUMN ember_schema.page_row.container_id
    IS 'The container these blocks belong to.';
