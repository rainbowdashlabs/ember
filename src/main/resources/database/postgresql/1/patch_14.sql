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

-- Page-image file system rework: per-station, hash-based dedup.
-- * station_id is denormalized so storage paths and dedup lookups don't have to join station_page.
-- * content_hash is a hex SHA-256 of the raw bytes. Identical uploads within the same station
--   reuse the same row + on-disk file; uploads in different stations get separate rows + files
--   so that station isolation is preserved.
-- * page_id now SET NULL on delete: with per-station dedup, the same image can be used by
--   several pages, so deleting one page must not destroy the row. The orphan cleanup at the
--   service layer reaps rows that are no longer referenced from any cell in the station.
ALTER TABLE ember_schema.page_image
    ADD COLUMN station_id   INTEGER REFERENCES ember_schema.station(id) ON DELETE CASCADE,
    ADD COLUMN content_hash TEXT;

UPDATE ember_schema.page_image pi
    SET station_id = sp.station_id
    FROM ember_schema.station_page sp
    WHERE sp.id = pi.page_id
      AND pi.station_id IS NULL;

ALTER TABLE ember_schema.page_image
    ALTER COLUMN station_id SET NOT NULL,
    ALTER COLUMN page_id    DROP NOT NULL;

-- Swap the page_id FK from CASCADE to SET NULL. The legacy constraint was created without an
-- explicit name (defaulted to page_image_page_id_fkey).
ALTER TABLE ember_schema.page_image
    DROP CONSTRAINT page_image_page_id_fkey,
    ADD CONSTRAINT page_image_page_id_fkey
        FOREIGN KEY (page_id) REFERENCES ember_schema.station_page(id) ON DELETE SET NULL;

-- Per-station dedup. content_hash stays nullable while legacy rows have not been hashed yet; the
-- partial unique index only kicks in once a hash has been populated.
CREATE UNIQUE INDEX page_image_station_hash_idx
    ON ember_schema.page_image (station_id, content_hash)
    WHERE content_hash IS NOT NULL;

CREATE INDEX page_image_station_idx
    ON ember_schema.page_image (station_id);

COMMENT ON COLUMN ember_schema.page_image.station_id IS 'Owning station. Mirrors station_page.station_id; lets the storage layer build paths and enforce per-station dedup without joining.';
COMMENT ON COLUMN ember_schema.page_image.content_hash IS 'Lowercase hex SHA-256 of the file bytes. NULL for legacy rows. Combined with station_id forms the on-disk filename.';
COMMENT ON COLUMN ember_schema.page_image.page_id IS 'The page that originally owned this image. Nullable so a row survives the deletion of its owning page when other pages in the same station reference the same image via dedup.';
