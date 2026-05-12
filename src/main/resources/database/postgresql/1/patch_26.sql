CREATE TABLE ember_schema.station_application
(
    id             SERIAL PRIMARY KEY,
    first_name     TEXT        NOT NULL,
    last_name      TEXT        NOT NULL,
    email          TEXT        NOT NULL,
    station_name   TEXT        NOT NULL,
    introduction   TEXT        NOT NULL DEFAULT '',
    verification_token TEXT,
    status         TEXT        NOT NULL DEFAULT 'unverified',
    deny_reason    TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at    TIMESTAMPTZ
);

CREATE INDEX idx_station_application_status ON ember_schema.station_application (status);
