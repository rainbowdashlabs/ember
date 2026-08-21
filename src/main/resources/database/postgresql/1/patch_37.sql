-- The document store of a station's members.
--
-- A document belongs to the station and is bound to the members it concerns, which is a set rather
-- than a single owner: one agreement can be the agreement of several people, and binding it twice
-- would make two documents out of one.

CREATE TABLE IF NOT EXISTS ember_schema.member_document
(
    id              SERIAL PRIMARY KEY,
    station_id      INTEGER   NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    title           TEXT      NOT NULL,
    file_name       TEXT      NOT NULL,
    mime_type       TEXT      NOT NULL,
    size_bytes      BIGINT    NOT NULL,
    hidden          BOOLEAN   NOT NULL DEFAULT FALSE,
    keep_on_archive BOOLEAN   NOT NULL DEFAULT FALSE,
    has_thumbnail   BOOLEAN   NOT NULL DEFAULT FALSE,
    uploaded_by     INTEGER            REFERENCES ember_schema.station_member (id) ON DELETE SET NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);

COMMENT ON TABLE ember_schema.member_document
    IS 'A file kept for one or more members of a station.';
COMMENT ON COLUMN ember_schema.member_document.title
    IS 'What the document is called, which is what a reader sees rather than the file name.';
COMMENT ON COLUMN ember_schema.member_document.file_name
    IS 'The name the file was uploaded under, used when it is downloaded again.';
COMMENT ON COLUMN ember_schema.member_document.hidden
    IS 'Whether the document is kept from the members it belongs to and shown only to whoever may read other members.';
COMMENT ON COLUMN ember_schema.member_document.keep_on_archive
    IS 'Whether the document survives its members being marked former. What binds legally has to outlast the membership.';
COMMENT ON COLUMN ember_schema.member_document.has_thumbnail
    IS 'Whether a picture of the document was produced, which is what a tile shows instead of an icon.';

CREATE TABLE IF NOT EXISTS ember_schema.member_document_member
(
    document_id INTEGER NOT NULL REFERENCES ember_schema.member_document (id) ON DELETE CASCADE,
    member_id   INTEGER NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    PRIMARY KEY (document_id, member_id)
);

COMMENT ON TABLE ember_schema.member_document_member
    IS 'Which members a document is bound to. A document with no member left is deleted with the last one.';

CREATE TABLE IF NOT EXISTS ember_schema.member_document_tag
(
    id         SERIAL PRIMARY KEY,
    station_id INTEGER NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    name       TEXT    NOT NULL,
    UNIQUE (station_id, name)
);

COMMENT ON TABLE ember_schema.member_document_tag
    IS 'Words a station sorts its documents by. Written as they are needed rather than set up in advance.';

CREATE TABLE IF NOT EXISTS ember_schema.member_document_tag_entry
(
    document_id INTEGER NOT NULL REFERENCES ember_schema.member_document (id) ON DELETE CASCADE,
    tag_id      INTEGER NOT NULL REFERENCES ember_schema.member_document_tag (id) ON DELETE CASCADE,
    PRIMARY KEY (document_id, tag_id)
);

CREATE TABLE IF NOT EXISTS ember_schema.member_document_search
(
    document_id INTEGER  NOT NULL PRIMARY KEY REFERENCES ember_schema.member_document (id) ON DELETE CASCADE,
    search_text TSVECTOR NOT NULL
);

COMMENT ON TABLE ember_schema.member_document_search
    IS 'What can be read out of a document, so a store of hundreds can be searched rather than scrolled.';

CREATE INDEX IF NOT EXISTS idx_member_document_search
    ON ember_schema.member_document_search USING GIN (search_text);

CREATE INDEX IF NOT EXISTS idx_member_document_station
    ON ember_schema.member_document (station_id);
CREATE INDEX IF NOT EXISTS idx_member_document_member_member
    ON ember_schema.member_document_member (member_id);

INSERT INTO ember_schema.station_permission (name)
VALUES ('MEMBER_SELF_UPLOAD')
ON CONFLICT (name) DO NOTHING;
