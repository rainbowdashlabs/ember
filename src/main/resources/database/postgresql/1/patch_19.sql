-- The legacy enum value PAGE_IMAGES was a historical mislabel: the rows it tracked
-- correspond to data/page-files/ on disk, which the new model names PAGE_FILES.
-- Rename the rows in place so the new code reads the same totals from day one.

UPDATE ember_schema.station_storage_usage
SET category = 'PAGE_FILES'
WHERE category = 'PAGE_IMAGES';

-- The AVATARS and IMAGES rollup categories are no longer written by reconciliation;
-- bytes are now tracked per image category (IMAGE_AVATAR, IMAGE_LOST_AND_FOUND,
-- IMAGE_QUIZ_QUESTION, IMAGE_KB_ICON, IMAGE_KB_IMAGE). Drop the stale aggregate rows so
-- existing values do not skew the admin storage view.

DELETE FROM ember_schema.station_storage_usage
WHERE category IN ('AVATARS', 'IMAGES');
