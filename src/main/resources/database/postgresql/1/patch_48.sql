-- Deleting an article or a folder becomes something that can be taken back.
--
-- Until now the delete button was final: the row went, and the cascade took the versions, the
-- comments, the tags, the grants and the shares with it. That is the right end state and the wrong
-- first step. Somebody clearing up a branch of the wiki cannot see, at the moment they click, which
-- of the twenty entries in front of them the shift leader wrote last winter, and there was nothing
-- between the click and the loss.
--
-- Marking rather than removing keeps the whole entry where it stands, which is what lets the reader
-- who deleted it be the one who puts it back: permission in the wiki is read along the folder path,
-- and the path is still there. Nothing is moved, nothing is copied, nothing is reparented, so a
-- restore is the same one column set back to NULL.

ALTER TABLE ember_schema.kb_file
    ADD COLUMN deleted_at          TIMESTAMPTZ,
    ADD COLUMN deleted_by          INT REFERENCES ember_schema.station_member (id) ON DELETE SET NULL,
    ADD COLUMN deleted_with_folder BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE ember_schema.kb_folder
    ADD COLUMN deleted_at          TIMESTAMPTZ,
    ADD COLUMN deleted_by          INT REFERENCES ember_schema.station_member (id) ON DELETE SET NULL,
    ADD COLUMN deleted_with_folder BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN ember_schema.kb_file.deleted_at IS
    'When the article was put in the trash, NULL while it is in use. Every listing, search and share reads only rows where this is NULL, so a marked article is gone everywhere without anything being removed.';
COMMENT ON COLUMN ember_schema.kb_file.deleted_by IS
    'The member who put the article in the trash, NULL when they have since left the station.';
COMMENT ON COLUMN ember_schema.kb_file.deleted_with_folder IS
    'True where the article went to the trash because the folder around it did. Such an article is not its own entry in the trash, and it comes back when that folder comes back rather than on its own.';
COMMENT ON COLUMN ember_schema.kb_folder.deleted_at IS
    'When the folder was put in the trash, NULL while it is in use.';
COMMENT ON COLUMN ember_schema.kb_folder.deleted_by IS
    'The member who put the folder in the trash, NULL when they have since left the station.';
COMMENT ON COLUMN ember_schema.kb_folder.deleted_with_folder IS
    'True where the folder went to the trash because a folder above it did. It is restored with that one rather than listed beside it.';

CREATE INDEX idx_kb_file_deleted_at ON ember_schema.kb_file (deleted_at) WHERE deleted_at IS NOT NULL;
CREATE INDEX idx_kb_folder_deleted_at ON ember_schema.kb_folder (deleted_at) WHERE deleted_at IS NOT NULL;

-- A folder in the trash must stop reserving its name.
--
-- Folder names are unique beside their siblings. Left as it is, a folder called Einsatz that somebody
-- deleted a fortnight ago goes on refusing every new folder called Einsatz at that spot, and the
-- refusal names a folder nobody can see. Restricting the rule to the folders still in use is the
-- whole fix, and it keeps the rule exactly as strict for everything visible.

ALTER TABLE ember_schema.kb_folder
    DROP CONSTRAINT kb_folder_station_id_parent_id_name_key;

CREATE UNIQUE INDEX kb_folder_name_unique
    ON ember_schema.kb_folder (station_id, parent_id, name)
    WHERE deleted_at IS NULL;

COMMENT ON INDEX ember_schema.kb_folder_name_unique IS
    'One folder of a given name beside its siblings, counting only the folders still in use. A folder in the trash keeps its name but stops reserving it, so clearing up and starting again does not run into a collision with something invisible.';
