-- Export/Import transfer tokens
CREATE TABLE ember_schema.transfer_token
(
    id         SERIAL PRIMARY KEY,
    station_id INTEGER   NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    token      TEXT      NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    expires_at TIMESTAMP NOT NULL,
    used       BOOLEAN   NOT NULL DEFAULT FALSE
);
