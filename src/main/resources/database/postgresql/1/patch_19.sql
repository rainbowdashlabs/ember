-- Phase 1 of the storage-backend rollout (.concept/storage-backends.md).
-- The legacy enum value PAGE_IMAGES was a historical mislabel: the rows it tracked
-- correspond to data/page-files/ on disk, which the new model names PAGE_FILES.
-- Rename the rows in place so the new code reads the same totals from day one.

UPDATE ember_schema.station_storage_usage
SET category = 'PAGE_FILES'
WHERE category = 'PAGE_IMAGES';
