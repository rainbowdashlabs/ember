-- A station keeps one map pin per place text its appointments name, such as "Gerätehaus". The pin is
-- found or corrected once and shown on every appointment naming that place. The field value itself
-- stays plain text; the pin is looked up by the text in its normal form.

CREATE TABLE ember_schema.station_place
(
    station_id INTEGER       NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    place_key  TEXT          NOT NULL,
    place_text TEXT          NOT NULL,
    latitude   NUMERIC(9, 6) NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude  NUMERIC(9, 6) NOT NULL CHECK (longitude BETWEEN -180 AND 180),
    precision  TEXT          NULL CHECK (precision IN ('EXACT', 'STREET', 'AREA')),
    updated_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (station_id, place_key)
);

COMMENT ON TABLE ember_schema.station_place IS
    'Map pins for the place texts a station''s appointments name, one per station and place text. Rows no appointment names any more are harmless and stay.';
COMMENT ON COLUMN ember_schema.station_place.station_id IS
    'The station the place belongs to. Deleting the station deletes its places.';
COMMENT ON COLUMN ember_schema.station_place.place_key IS
    'The place text in its normal form: trimmed, in lower case, every run of whitespace made one space. Appointments find their pin by it.';
COMMENT ON COLUMN ember_schema.station_place.place_text IS
    'The place text as it was last saved, for display.';
COMMENT ON COLUMN ember_schema.station_place.latitude IS
    'WGS-84 latitude of the pin in degrees, [-90, 90].';
COMMENT ON COLUMN ember_schema.station_place.longitude IS
    'WGS-84 longitude of the pin in degrees, [-180, 180].';
COMMENT ON COLUMN ember_schema.station_place.precision IS
    'How closely the address search matched when it placed the pin: EXACT, STREET or AREA. NULL when the pin was placed or moved by hand.';
COMMENT ON COLUMN ember_schema.station_place.updated_at IS
    'When the pin was last saved.';
