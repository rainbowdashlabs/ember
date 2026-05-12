ALTER TABLE ember_schema.station_event
    ADD COLUMN requires_registration BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN registration_deadline TIMESTAMP,
    ADD COLUMN requires_confirmation BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE ember_schema.event_registration
(
    id         SERIAL PRIMARY KEY,
    event_id   INTEGER NOT NULL REFERENCES ember_schema.station_event (id) ON DELETE CASCADE,
    member_id  INTEGER NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    event_date DATE    NOT NULL,
    status     TEXT    NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (event_id, member_id, event_date)
);

CREATE INDEX idx_event_registration_event ON ember_schema.event_registration (event_id);
CREATE INDEX idx_event_registration_member ON ember_schema.event_registration (member_id);
