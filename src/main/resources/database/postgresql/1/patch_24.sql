-- Scope entity notes to their owning station.
-- Notes were uniquely keyed on (entity_type, entity_id) only, which let a note be
-- addressed without regard to the station it belongs to. Re-key on the station so a
-- station only ever reads or writes its own notes.
ALTER TABLE ember_schema.entity_note
    DROP CONSTRAINT IF EXISTS entity_note_entity_type_entity_id_key;

ALTER TABLE ember_schema.entity_note
    ADD CONSTRAINT entity_note_station_entity_key UNIQUE (station_id, entity_type, entity_id);

-- Track the last accepted TOTP time-step per factor so a code cannot be replayed
-- within its validity window.
ALTER TABLE ember_schema.account_2fa_totp
    ADD COLUMN last_used_step BIGINT NOT NULL DEFAULT 0;
COMMENT ON COLUMN ember_schema.account_2fa_totp.last_used_step IS
    'Highest TOTP time-step already consumed; verification rejects any step at or below it.';
