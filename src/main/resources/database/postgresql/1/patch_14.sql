-- Station geolocation: opt-in postal address + WGS-84 coordinates.

ALTER TABLE ember_schema.station
    ADD COLUMN address_line TEXT,
    ADD COLUMN postal_code  TEXT,
    ADD COLUMN city         TEXT,
    ADD COLUMN country      TEXT,
    ADD COLUMN latitude     NUMERIC(9, 6),
    ADD COLUMN longitude    NUMERIC(9, 6);

COMMENT ON COLUMN ember_schema.station.country
    IS 'ISO-3166-1 alpha-2 (e.g. ''DE''). Free-form text but admin UI restricts the input set.';
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
