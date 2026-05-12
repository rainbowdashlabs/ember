-- Allow same field name across different scopes
ALTER TABLE ember_schema.profile_field DROP CONSTRAINT profile_field_station_id_name_key;
ALTER TABLE ember_schema.profile_field ADD CONSTRAINT profile_field_station_id_scope_name_key UNIQUE (station_id, scope, name);

-- Profile field change history with acknowledgements and comments

CREATE TABLE ember_schema.profile_field_change
(
    id          SERIAL PRIMARY KEY,
    field_id    INTEGER   NOT NULL REFERENCES ember_schema.profile_field (id) ON DELETE CASCADE,
    member_id   INTEGER   NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    old_value   JSONB,
    new_value   JSONB,
    changed_by  INTEGER   NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    changed_at  TIMESTAMP NOT NULL DEFAULT now(),
    requires_acknowledgement      BOOLEAN   NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_profile_field_change_member ON ember_schema.profile_field_change (member_id);
CREATE INDEX idx_profile_field_change_field ON ember_schema.profile_field_change (field_id);
CREATE INDEX idx_profile_field_change_changed_at ON ember_schema.profile_field_change (member_id, changed_at DESC);

CREATE TABLE ember_schema.profile_field_change_acknowledgement
(
    id              SERIAL PRIMARY KEY,
    change_id       INTEGER   NOT NULL REFERENCES ember_schema.profile_field_change (id) ON DELETE CASCADE,
    acknowledged_by INTEGER   NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    acknowledged_at TIMESTAMP NOT NULL DEFAULT now(),
    comment         TEXT,
    UNIQUE (change_id, acknowledged_by)
);

CREATE INDEX idx_profile_field_change_ack_change ON ember_schema.profile_field_change_acknowledgement (change_id);
