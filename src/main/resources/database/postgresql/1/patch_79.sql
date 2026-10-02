ALTER TABLE ember_schema.discovery_station_cache
    ADD COLUMN logo_checked_at    TIMESTAMPTZ NULL,
    ADD COLUMN logo_stored        BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN logo_etag          TEXT        NULL,
    ADD COLUMN logo_last_modified TEXT        NULL;

COMMENT ON COLUMN ember_schema.discovery_station_cache.logo_checked_at IS
    'When this instance last asked the other instance for the station''s logo. NULL until it has asked once.';
COMMENT ON COLUMN ember_schema.discovery_station_cache.logo_stored IS
    'Whether this instance keeps a copy of the station''s logo, which the discovery page shows in place of the other instance''s own.';
COMMENT ON COLUMN ember_schema.discovery_station_cache.logo_etag IS
    'The entity tag the other instance sent with the logo copied here, asked back with so an unchanged logo is not sent again. NULL when none was sent.';
COMMENT ON COLUMN ember_schema.discovery_station_cache.logo_last_modified IS
    'The modification date the other instance sent with the logo copied here, as it was sent. NULL when none was sent.';
