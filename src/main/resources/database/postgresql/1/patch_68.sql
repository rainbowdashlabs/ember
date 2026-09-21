DROP TABLE ember_schema.kb_favourite;

CREATE TABLE ember_schema.kb_favourite
(
    id                  SERIAL PRIMARY KEY,
    member_id           INTEGER     NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    target              TEXT        NOT NULL,
    file_id             INTEGER REFERENCES ember_schema.kb_file (id) ON DELETE CASCADE,
    folder_id           INTEGER REFERENCES ember_schema.kb_folder (id) ON DELETE CASCADE,
    partner_station_uid UUID,
    partner_entry_id    INTEGER,
    title               TEXT,
    file_type           TEXT,
    station_name        TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT kb_favourite_target_columns CHECK (
        (target = 'FILE'
            AND file_id IS NOT NULL AND folder_id IS NULL
            AND partner_station_uid IS NULL AND partner_entry_id IS NULL)
        OR (target = 'FOLDER'
            AND folder_id IS NOT NULL AND file_id IS NULL
            AND partner_station_uid IS NULL AND partner_entry_id IS NULL)
        OR (target IN ('PARTNER_FILE', 'PARTNER_FOLDER')
            AND partner_station_uid IS NOT NULL AND partner_entry_id IS NOT NULL
            AND file_id IS NULL AND folder_id IS NULL)
    )
);

CREATE UNIQUE INDEX uq_kb_favourite_file ON ember_schema.kb_favourite (member_id, file_id)
    WHERE target = 'FILE';
CREATE UNIQUE INDEX uq_kb_favourite_folder ON ember_schema.kb_favourite (member_id, folder_id)
    WHERE target = 'FOLDER';
CREATE UNIQUE INDEX uq_kb_favourite_partner ON ember_schema.kb_favourite (member_id, target, partner_station_uid, partner_entry_id)
    WHERE target IN ('PARTNER_FILE', 'PARTNER_FOLDER');
CREATE INDEX idx_kb_favourite_member ON ember_schema.kb_favourite (member_id, created_at DESC);

COMMENT ON TABLE ember_schema.kb_favourite IS
    'What one member marked in the wiki to reach quickly: a file or a folder of this station, or one a partner station shares. Only that member sees it.';
COMMENT ON COLUMN ember_schema.kb_favourite.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.kb_favourite.member_id IS 'The member whose favourite this is.';
COMMENT ON COLUMN ember_schema.kb_favourite.target IS
    'What is marked: FILE or FOLDER of this station, PARTNER_FILE or PARTNER_FOLDER of a partner station. Decides which of the pointing columns are set.';
COMMENT ON COLUMN ember_schema.kb_favourite.file_id IS 'The file of this station, for a FILE favourite. The favourite goes when the file does.';
COMMENT ON COLUMN ember_schema.kb_favourite.folder_id IS 'The folder of this station, for a FOLDER favourite. The favourite goes when the folder does.';
COMMENT ON COLUMN ember_schema.kb_favourite.partner_station_uid IS 'The partner station sharing the entry, for a partner favourite.';
COMMENT ON COLUMN ember_schema.kb_favourite.partner_entry_id IS 'The file or folder id on the partner station, for a partner favourite.';
COMMENT ON COLUMN ember_schema.kb_favourite.title IS
    'The partner entry''s name as the partner last gave it, so the tile can be drawn without asking the partner. Empty for this station''s entries, whose name is read live.';
COMMENT ON COLUMN ember_schema.kb_favourite.file_type IS 'The partner file''s kind as the partner last gave it. Empty otherwise.';
COMMENT ON COLUMN ember_schema.kb_favourite.station_name IS 'The partner station''s name as it last gave it. Empty for this station''s entries.';
COMMENT ON COLUMN ember_schema.kb_favourite.created_at IS 'When the member marked it, which orders the list newest first.';
