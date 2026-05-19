-- Lost and Found feature
INSERT INTO ember_schema.role (name) VALUES ('lost_and_found_management') ON CONFLICT DO NOTHING;

CREATE TABLE ember_schema.lost_and_found_item
(
    id           SERIAL PRIMARY KEY,
    station_id   INTEGER   NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    description  TEXT,
    found_at     DATE      NOT NULL DEFAULT CURRENT_DATE,
    image        BYTEA,
    image_content_type TEXT,
    claimed_by   INTEGER REFERENCES ember_schema.station_member (id) ON DELETE SET NULL,
    claimed_at   TIMESTAMP,
    created_by   INTEGER   NOT NULL REFERENCES ember_schema.station_member (id),
    created_at   TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_lost_found_station ON ember_schema.lost_and_found_item (station_id);
