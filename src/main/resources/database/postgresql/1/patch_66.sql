-- A station draws the same table of people twice: who is coming to an appointment, and who is on the
-- register. Which columns it wants is a property of the station and not of either screen, so it is
-- saved once and named, the way an attendance report preset already is.
--
-- The columns are kept as a list rather than as a row per column because nothing ever asks about one
-- column on its own: a selection is read whole, written whole, and means nothing in pieces. What a
-- reader may actually see is decided when the table is drawn and never here, so a selection naming a
-- field the reader may not read is not wrong, it is simply trimmed.

CREATE TABLE ember_schema.member_table_preset
(
    id         SERIAL PRIMARY KEY,
    station_id INTEGER NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    name       TEXT    NOT NULL,
    columns    JSONB   NOT NULL DEFAULT '[]',
    UNIQUE (station_id, name)
);

COMMENT ON TABLE ember_schema.member_table_preset IS
    'A named set of columns a station wants when it lists people, used both for who is coming to an appointment and for the register. Saved per station rather than per screen, so the two never drift apart.';
COMMENT ON COLUMN ember_schema.member_table_preset.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.member_table_preset.station_id IS 'The station whose selection this is.';
COMMENT ON COLUMN ember_schema.member_table_preset.name IS 'What the station calls this selection, unique within the station so a picker can name them.';
COMMENT ON COLUMN ember_schema.member_table_preset.columns IS
    'The chosen columns in order, as JSON objects carrying a kind and what it points at: a builtin name, a profile field id, or an appointment question id. Held whole because a selection is only ever read and written whole.';

CREATE INDEX idx_member_table_preset_station ON ember_schema.member_table_preset (station_id);
