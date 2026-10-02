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

CREATE TABLE IF NOT EXISTS ember_schema.federation_pair_request
(
    id                   SERIAL PRIMARY KEY,
    station_id           INTEGER     NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    direction            TEXT        NOT NULL CHECK (direction IN ('INCOMING', 'OUTGOING')),
    remote_station_uid   UUID        NOT NULL,
    remote_station_name  TEXT        NOT NULL,
    remote_base_url      TEXT        NOT NULL,
    remote_instance_key  TEXT        NOT NULL,
    remote_public_key    TEXT        NULL,
    remote_contract      JSONB       NULL,
    status               TEXT        NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED')),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    answered_at          TIMESTAMPTZ NULL,
    checked_at           TIMESTAMPTZ NULL,
    UNIQUE (station_id, direction, remote_station_uid)
);

CREATE INDEX IF NOT EXISTS idx_federation_pair_request_instance
    ON ember_schema.federation_pair_request (remote_instance_key, created_at);

COMMENT ON TABLE ember_schema.federation_pair_request IS
    'Requests to federate between a station of this instance and a station of another instance, in either direction, until they are answered and for a while after.';
COMMENT ON COLUMN ember_schema.federation_pair_request.id IS
    'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.federation_pair_request.station_id IS
    'The station of this instance: the one asked for an INCOMING request, the one asking for an OUTGOING one.';
COMMENT ON COLUMN ember_schema.federation_pair_request.direction IS
    'INCOMING when a station of another instance asked a station here, OUTGOING when a station here asked one of another instance.';
COMMENT ON COLUMN ember_schema.federation_pair_request.remote_station_uid IS
    'The station of the other instance, as that instance knows it.';
COMMENT ON COLUMN ember_schema.federation_pair_request.remote_station_name IS
    'The name of the station of the other instance, as it was sent, shown as text only.';
COMMENT ON COLUMN ember_schema.federation_pair_request.remote_base_url IS
    'Where the other instance is reached, with its port.';
COMMENT ON COLUMN ember_schema.federation_pair_request.remote_instance_key IS
    'The discovery key of the other instance, which signs every message about this request.';
COMMENT ON COLUMN ember_schema.federation_pair_request.remote_public_key IS
    'The federation key of the station of the other instance: sent with an INCOMING request, received with the answer to an OUTGOING one. NULL until known.';
COMMENT ON COLUMN ember_schema.federation_pair_request.remote_contract IS
    'The federation contract the other instance spoke when it sent the request or its answer. NULL until known.';
COMMENT ON COLUMN ember_schema.federation_pair_request.status IS
    'PENDING until the asked station answers, then ACCEPTED or DECLINED.';
COMMENT ON COLUMN ember_schema.federation_pair_request.created_at IS
    'When the request was sent or received. A request sent again after a decline starts here anew.';
COMMENT ON COLUMN ember_schema.federation_pair_request.answered_at IS
    'When the request was answered. A decline keeps the same station from asking again for 30 days from here. NULL while pending.';
COMMENT ON COLUMN ember_schema.federation_pair_request.checked_at IS
    'When this instance last asked the other instance about an OUTGOING request. NULL until it has asked once, and for INCOMING requests.';
