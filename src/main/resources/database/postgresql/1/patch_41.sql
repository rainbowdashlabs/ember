-- The storage categories that were renamed in the release without their recorded usage.
--
-- Page files and page images became the media library. The enum was renamed, the rows counting how
-- much of each a station holds were not, so every instance that had stored a page file kept rows
-- naming a category the running version no longer knows. Reading them back is what the operator's
-- storage overview does, and it stopped on the first one.
--
-- Added rather than replaced: a station may already hold a row under the new name, and the pair
-- (station, category) is the primary key. The two counts are one count now, which is what the rename
-- meant in the first place.
INSERT INTO ember_schema.station_storage_usage (station_id, category, total_bytes, file_count, updated_at)
SELECT station_id, 'MEDIA_FILES', total_bytes, file_count, now()
FROM ember_schema.station_storage_usage
WHERE category = 'PAGE_FILES'
ON CONFLICT (station_id, category) DO UPDATE
    SET total_bytes = station_storage_usage.total_bytes + excluded.total_bytes,
        file_count  = station_storage_usage.file_count + excluded.file_count,
        updated_at  = now();

DELETE FROM ember_schema.station_storage_usage WHERE category = 'PAGE_FILES';

-- The earlier name, for an instance that was never carried through the rename to PAGE_FILES.
INSERT INTO ember_schema.station_storage_usage (station_id, category, total_bytes, file_count, updated_at)
SELECT station_id, 'MEDIA_IMAGES', total_bytes, file_count, now()
FROM ember_schema.station_storage_usage
WHERE category = 'PAGE_IMAGES'
ON CONFLICT (station_id, category) DO UPDATE
    SET total_bytes = station_storage_usage.total_bytes + excluded.total_bytes,
        file_count  = station_storage_usage.file_count + excluded.file_count,
        updated_at  = now();

DELETE FROM ember_schema.station_storage_usage WHERE category = 'PAGE_IMAGES';

COMMENT ON COLUMN ember_schema.station_storage_usage.category IS
    'Storage category, named exactly as the StorageCategory the application knows. A row naming anything else is left out of the overview rather than read.';

-- What the audience of an event template holds, said in the schema rather than only in the release
-- that added the columns.
COMMENT ON TABLE ember_schema.event_template_restriction IS
    'Who the appointments written from an event template are for. Not a restriction on the template itself: it is copied onto every appointment made from it, and the appointment owns it from there.';
COMMENT ON COLUMN ember_schema.event_template_restriction.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.event_template_restriction.template_id IS 'References the event template.';
COMMENT ON COLUMN ember_schema.event_template_restriction.user_type IS
    'Required member type. Exactly one of user_type/group_id/tag_id/member_id must be set.';
COMMENT ON COLUMN ember_schema.event_template_restriction.group_id IS 'Required group membership.';
COMMENT ON COLUMN ember_schema.event_template_restriction.tag_id IS 'Required tag.';
COMMENT ON COLUMN ember_schema.event_template_restriction.member_id IS
    'Specific member (always OR-connected, bypasses AND/OR mode).';
