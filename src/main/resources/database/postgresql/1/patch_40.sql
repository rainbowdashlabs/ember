-- A media library that belongs to the instance rather than to a station.
--
-- A system entry is read in every station, so the pictures in it cannot belong to one of them: a
-- station pruning its unused files would break a notice everywhere else. The instance keeps its own
-- files, with no station, and serves them to everyone.
--
-- The library stores one copy of identical bytes, which it enforces with a unique index on the
-- station and the hash together. PostgreSQL counts two nulls as different values by default, so
-- that index would have let the instance store the same picture as many times as it was uploaded.
-- Recreating it with NULLS NOT DISTINCT makes the absence of a station a value like any other, and
-- the instance deduplicates exactly as a station does.
ALTER TABLE ember_schema.station_file
    ALTER COLUMN station_id DROP NOT NULL;

COMMENT ON COLUMN ember_schema.station_file.station_id IS
    'The station whose library holds the file. Null for a file the instance holds, which every station can be served.';

DROP INDEX ember_schema.station_file_station_hash_idx;

CREATE UNIQUE INDEX station_file_station_hash_idx
    ON ember_schema.station_file (station_id, content_hash) NULLS NOT DISTINCT
    WHERE content_hash IS NOT NULL;

-- Who brought a file in is recorded per station member. An instance file is brought in by an
-- administrator, who is not a member of anything, so it simply has no uploader to record.
