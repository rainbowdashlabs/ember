-- Discovery settings: allow stations to opt into being discoverable
ALTER TABLE ember_schema.station
    ADD COLUMN discovery_visibility TEXT NOT NULL DEFAULT 'NONE',
    ADD COLUMN discovery_description TEXT,
    ADD COLUMN discovery_show_kb BOOLEAN NOT NULL DEFAULT FALSE;

-- Station-generated invite tokens (consent already given by the generating station)
CREATE TABLE ember_schema.federation_invite_token (
    id         SERIAL PRIMARY KEY,
    station_id INT  NOT NULL REFERENCES ember_schema.station(id) ON DELETE CASCADE,
    token      TEXT NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_federation_invite_token ON ember_schema.federation_invite_token(token);

