-- Registration codes (station-specific, controls who can self-register)
CREATE TABLE ember_schema.registration_code
(
    id         SERIAL PRIMARY KEY,
    station_id INTEGER NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    code       TEXT    NOT NULL,
    max_uses   INTEGER NOT NULL DEFAULT -1, -- -1 = unlimited
    uses       INTEGER NOT NULL DEFAULT 0,
    UNIQUE (code)
);

-- Groups assigned to users who register with a code
CREATE TABLE ember_schema.registration_code_group
(
    code_id  INTEGER NOT NULL REFERENCES ember_schema.registration_code (id) ON DELETE CASCADE,
    group_id INTEGER NOT NULL REFERENCES ember_schema.member_group (id) ON DELETE CASCADE,
    PRIMARY KEY (code_id, group_id)
);
