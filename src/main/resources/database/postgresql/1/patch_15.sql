-- Per-station traffic monitoring (phase 1 of station-traffic-monitoring concept)
-- Hourly aggregated ingress/egress byte counters and request counts, split by
-- auth bucket (AUTHENTICATED / UNAUTHENTICATED / FEDERATION). station_id is
-- nullable because admin and instance-global routes do not have a station; the
-- two partial unique indexes give us proper conflict targets for the upsert in
-- both the per-station and the global case (PostgreSQL treats NULLs as
-- distinct in a regular unique constraint).

CREATE TABLE ember_schema.station_traffic_hourly (
    hour          TIMESTAMPTZ NOT NULL,
    station_id    INTEGER NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    auth          TEXT NOT NULL,
    ingress_bytes BIGINT NOT NULL DEFAULT 0,
    egress_bytes  BIGINT NOT NULL DEFAULT 0,
    requests      BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX station_traffic_hourly_uq_station
    ON ember_schema.station_traffic_hourly (hour, station_id, auth)
    WHERE station_id IS NOT NULL;

CREATE UNIQUE INDEX station_traffic_hourly_uq_global
    ON ember_schema.station_traffic_hourly (hour, auth)
    WHERE station_id IS NULL;

CREATE INDEX station_traffic_hourly_station_hour_idx
    ON ember_schema.station_traffic_hourly (station_id, hour DESC);

CREATE INDEX station_traffic_hourly_hour_idx
    ON ember_schema.station_traffic_hourly (hour);

COMMENT ON TABLE ember_schema.station_traffic_hourly IS
    'Hourly aggregated traffic counters per (station, auth bucket). Written by StationTrafficRecorder, pruned by retention.';
COMMENT ON COLUMN ember_schema.station_traffic_hourly.hour IS 'Bucket start, truncated to the hour (UTC).';
COMMENT ON COLUMN ember_schema.station_traffic_hourly.station_id IS 'Owning station, or NULL for instance-global traffic.';
COMMENT ON COLUMN ember_schema.station_traffic_hourly.auth IS 'AuthBucket enum: AUTHENTICATED, UNAUTHENTICATED, FEDERATION.';
COMMENT ON COLUMN ember_schema.station_traffic_hourly.ingress_bytes IS 'Sum of estimated request bytes (headers + body) in this bucket.';
COMMENT ON COLUMN ember_schema.station_traffic_hourly.egress_bytes IS 'Sum of estimated response bytes (headers + body) in this bucket.';
COMMENT ON COLUMN ember_schema.station_traffic_hourly.requests IS 'Number of requests that contributed to this bucket.';

-- Per-public-page hit counters (phase 4 of station-traffic-monitoring concept).
-- Aggregate-only: no IPs, no cookies, no per-visitor identifier. Each row holds a
-- bucketed count of hits for one (hour, page, country, referer_domain, is_bot)
-- combination. Country is derived from CF-IPCountry (or "XX" if missing); referer
-- is reduced to eTLD+1 at ingestion time so the raw header never reaches the DB.

CREATE TABLE ember_schema.page_hit_hourly (
    hour             TIMESTAMPTZ NOT NULL,
    page_id          INTEGER     NOT NULL REFERENCES ember_schema.station_page (id) ON DELETE CASCADE,
    country          CHAR(2)     NOT NULL DEFAULT 'XX',
    referer_domain   TEXT        NOT NULL DEFAULT 'direct',
    is_bot           BOOLEAN     NOT NULL DEFAULT FALSE,
    hits             BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (hour, page_id, country, referer_domain, is_bot)
);

CREATE INDEX page_hit_hourly_page_hour_idx
    ON ember_schema.page_hit_hourly (page_id, hour DESC);

CREATE INDEX page_hit_hourly_hour_idx
    ON ember_schema.page_hit_hourly (hour);

COMMENT ON TABLE ember_schema.page_hit_hourly IS
    'Hourly hit counters per public station_page, dimensioned by country, referer domain, and bot flag. No IPs or visitor identifiers.';
COMMENT ON COLUMN ember_schema.page_hit_hourly.hour IS 'Bucket start, truncated to the hour (UTC).';
COMMENT ON COLUMN ember_schema.page_hit_hourly.page_id IS 'Public station_page that was hit.';
COMMENT ON COLUMN ember_schema.page_hit_hourly.country IS 'ISO-3166 alpha-2 country code from CF-IPCountry; "XX" when missing or unknown.';
COMMENT ON COLUMN ember_schema.page_hit_hourly.referer_domain IS 'eTLD+1 of the Referer header; "direct" if absent, "internal" if our own host.';
COMMENT ON COLUMN ember_schema.page_hit_hourly.is_bot IS 'True when the User-Agent matched the bot denylist; counted separately so the dashboard can exclude bots.';
COMMENT ON COLUMN ember_schema.page_hit_hourly.hits IS 'Number of hits that contributed to this bucket.';
