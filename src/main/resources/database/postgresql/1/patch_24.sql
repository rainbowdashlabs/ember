-- Scope entity notes to their owning station.
-- Notes were uniquely keyed on (entity_type, entity_id) only, which let a note be
-- addressed without regard to the station it belongs to. Re-key on the station so a
-- station only ever reads or writes its own notes.
ALTER TABLE ember_schema.entity_note
    DROP CONSTRAINT IF EXISTS entity_note_entity_type_entity_id_key;

ALTER TABLE ember_schema.entity_note
    ADD CONSTRAINT entity_note_station_entity_key UNIQUE (station_id, entity_type, entity_id);
