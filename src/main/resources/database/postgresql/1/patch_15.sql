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
