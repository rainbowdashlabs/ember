ALTER TABLE ember_schema.profile_field
    ADD COLUMN IF NOT EXISTS shown_if_field_id INTEGER NULL REFERENCES ember_schema.profile_field (id) ON DELETE RESTRICT;

COMMENT ON COLUMN ember_schema.profile_field.shown_if_field_id IS
    'A yes/no field of the same station this field depends on, NULL when it depends on none. While a member is asked both and has not answered yes, this field is hidden and not required. A member asked only this field sees it as usual. Restricts deleting the yes/no field while anything depends on it.';

ALTER TABLE ember_schema.cluster_profile_field
    ADD COLUMN IF NOT EXISTS shown_if_field_id INTEGER NULL REFERENCES ember_schema.cluster_profile_field (id) ON DELETE RESTRICT;

COMMENT ON COLUMN ember_schema.cluster_profile_field.shown_if_field_id IS
    'A yes/no field of the same association this field depends on, NULL when it depends on none. While a member is asked both and has not answered yes, this field is hidden and not required. A member asked only this field sees it as usual. Restricts deleting the yes/no field while anything depends on it.';

CREATE TABLE IF NOT EXISTS ember_schema.profile_field_set
(
    id              SERIAL PRIMARY KEY,
    station_id      INTEGER NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    name            TEXT    NOT NULL,
    name_as_section BOOLEAN NOT NULL DEFAULT FALSE
);

COMMENT ON TABLE ember_schema.profile_field_set IS
    'A bundle of a station''s profile fields that belong together, such as an address. Its fields are put to an audience together and stand together on every form. Dissolving it leaves the fields as they are.';
COMMENT ON COLUMN ember_schema.profile_field_set.name IS
    'What the bundle is called, shown as a heading above its fields when name_as_section is set.';
COMMENT ON COLUMN ember_schema.profile_field_set.name_as_section IS
    'Whether the forms show the name as a heading above the fields, in place of a heading field placed by hand.';

ALTER TABLE ember_schema.profile_field
    ADD COLUMN IF NOT EXISTS field_set_id INTEGER NULL REFERENCES ember_schema.profile_field_set (id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS set_position INTEGER NOT NULL DEFAULT 0;

COMMENT ON COLUMN ember_schema.profile_field.field_set_id IS
    'The bundle of fields of the same station this field belongs to, NULL when it stands on its own. Cleared when the bundle is dissolved.';
COMMENT ON COLUMN ember_schema.profile_field.set_position IS
    'Where the field stands within its bundle, smallest first. Meaningless while it is in none.';

CREATE TABLE IF NOT EXISTS ember_schema.cluster_profile_field_set
(
    id              SERIAL PRIMARY KEY,
    cluster_id      INTEGER NOT NULL REFERENCES ember_schema.cluster (id) ON DELETE CASCADE,
    name            TEXT    NOT NULL,
    name_as_section BOOLEAN NOT NULL DEFAULT FALSE
);

COMMENT ON TABLE ember_schema.cluster_profile_field_set IS
    'A bundle of an association''s profile fields that belong together, such as an address. Its fields are put to an audience together and stand together on every form. Dissolving it leaves the fields as they are.';
COMMENT ON COLUMN ember_schema.cluster_profile_field_set.name IS
    'What the bundle is called, shown as a heading above its fields when name_as_section is set.';
COMMENT ON COLUMN ember_schema.cluster_profile_field_set.name_as_section IS
    'Whether the forms show the name as a heading above the fields, in place of a heading field placed by hand.';

ALTER TABLE ember_schema.cluster_profile_field
    ADD COLUMN IF NOT EXISTS field_set_id INTEGER NULL REFERENCES ember_schema.cluster_profile_field_set (id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS set_position INTEGER NOT NULL DEFAULT 0;

COMMENT ON COLUMN ember_schema.cluster_profile_field.field_set_id IS
    'The bundle of fields of the same association this field belongs to, NULL when it stands on its own. Cleared when the bundle is dissolved.';
COMMENT ON COLUMN ember_schema.cluster_profile_field.set_position IS
    'Where the field stands within its bundle, smallest first. Meaningless while it is in none.';
