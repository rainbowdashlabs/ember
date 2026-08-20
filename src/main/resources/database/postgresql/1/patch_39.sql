-- Blocks belong to a container, not to a page.
--
-- Rows and cells hung off a page, which is why the page editor could only ever build a page. A
-- container owns them instead, and a page, a news entry and a knowledge-base article each own one.
-- Nothing about the editor changes; what changes is what may be edited with it.

CREATE TABLE IF NOT EXISTS ember_schema.content_container
(
    id         SERIAL PRIMARY KEY,
    station_id INTEGER     NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS content_container_station_idx ON ember_schema.content_container (station_id);

COMMENT ON TABLE ember_schema.content_container
    IS 'The rows and cells of one piece of authored content. A page, a news entry or a knowledge-base article owns one; the container itself knows nothing about which.';

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
