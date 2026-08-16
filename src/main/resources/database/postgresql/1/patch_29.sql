CREATE TABLE ember_schema.event_registration_field
(
    id         SERIAL PRIMARY KEY,
    event_id   INTEGER NOT NULL REFERENCES ember_schema.station_event (id) ON DELETE CASCADE,
    name       TEXT    NOT NULL,
    field_type TEXT    NOT NULL,
    config     JSONB   NOT NULL DEFAULT '{}',
    position   INTEGER NOT NULL DEFAULT 0,
    overview   BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (event_id, name)
);

COMMENT ON TABLE ember_schema.event_registration_field IS 'A question the event asks of everyone registering for it, answered once per registration.';
COMMENT ON COLUMN ember_schema.event_registration_field.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.event_registration_field.event_id IS 'The event asking the question.';
COMMENT ON COLUMN ember_schema.event_registration_field.name IS 'Label shown above the input and next to the answer.';
COMMENT ON COLUMN ember_schema.event_registration_field.field_type IS 'Data type of the answer, from the shared event field type set (STRING, NUMBER, ENUM, MEMBER, ...).';
COMMENT ON COLUMN ember_schema.event_registration_field.config IS 'Field configuration as JSON: required, defaultValue, options, min, max and the member-field constraints.';
COMMENT ON COLUMN ember_schema.event_registration_field.position IS 'Sort order of the question within the registration form.';
COMMENT ON COLUMN ember_schema.event_registration_field.overview IS 'Whether the answer is shown next to the member in the registration list, rather than only on the registration itself.';

CREATE INDEX idx_event_registration_field_event ON ember_schema.event_registration_field (event_id);

CREATE TABLE ember_schema.event_registration_field_value
(
    registration_id INTEGER NOT NULL REFERENCES ember_schema.event_registration (id) ON DELETE CASCADE,
    field_id        INTEGER NOT NULL REFERENCES ember_schema.event_registration_field (id) ON DELETE CASCADE,
    value           TEXT    NOT NULL DEFAULT '',
    PRIMARY KEY (registration_id, field_id)
);

COMMENT ON TABLE ember_schema.event_registration_field_value IS 'One member''s answer to one registration question. Keyed by the registration rather than by member and event, because a recurring event is registered for separately per date.';
COMMENT ON COLUMN ember_schema.event_registration_field_value.registration_id IS 'The registration the answer belongs to; the answer is removed with it.';
COMMENT ON COLUMN ember_schema.event_registration_field_value.field_id IS 'The question being answered.';
COMMENT ON COLUMN ember_schema.event_registration_field_value.value IS 'The answer, stored as text in the same shape the event''s own field values use.';

CREATE INDEX idx_event_registration_field_value_field ON ember_schema.event_registration_field_value (field_id);

CREATE TABLE ember_schema.event_template_registration_field
(
    id          SERIAL PRIMARY KEY,
    template_id INTEGER NOT NULL REFERENCES ember_schema.event_template (id) ON DELETE CASCADE,
    name        TEXT    NOT NULL,
    field_type  TEXT    NOT NULL,
    config      JSONB   NOT NULL DEFAULT '{}',
    position    INTEGER NOT NULL DEFAULT 0,
    overview    BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (template_id, name)
);

COMMENT ON TABLE ember_schema.event_template_registration_field IS 'A registration question carried by an event template and copied into every event created from it.';
COMMENT ON COLUMN ember_schema.event_template_registration_field.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.event_template_registration_field.template_id IS 'The template carrying the question.';
COMMENT ON COLUMN ember_schema.event_template_registration_field.name IS 'Label shown above the input and next to the answer.';
COMMENT ON COLUMN ember_schema.event_template_registration_field.field_type IS 'Data type of the answer, from the shared event field type set.';
COMMENT ON COLUMN ember_schema.event_template_registration_field.config IS 'Field configuration as JSON, copied verbatim into the created event.';
COMMENT ON COLUMN ember_schema.event_template_registration_field.position IS 'Sort order of the question within the registration form.';
COMMENT ON COLUMN ember_schema.event_template_registration_field.overview IS 'Whether the answer is shown next to the member in the registration list.';

CREATE INDEX idx_event_template_registration_field_template ON ember_schema.event_template_registration_field (template_id);
