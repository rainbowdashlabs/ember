-- Appointment templates carry what an appointment made from them would otherwise need set by hand:
-- the sign-up rules, the partners it is shared with, the files attached to it and where the fields
-- of its attendance sheet take their values from.

ALTER TABLE ember_schema.event_template
    ADD COLUMN min_registrations       INTEGER CHECK (min_registrations >= 0),
    ADD COLUMN threshold_days          INTEGER CHECK (threshold_days >= 0),
    ADD COLUMN registration_close_days INTEGER CHECK (registration_close_days >= 0);

COMMENT ON COLUMN ember_schema.event_template.min_registrations
    IS 'Minimum number of accepted registrations an appointment made from the template asks for. Empty for no minimum.';
COMMENT ON COLUMN ember_schema.event_template.threshold_days
    IS 'How many days before each date the minimum must be reached, or the date is called off. Empty where no date is called off for too few registrations.';
COMMENT ON COLUMN ember_schema.event_template.registration_close_days
    IS 'How many days before each date registration closes. Empty for no automatic close.';
COMMENT ON COLUMN ember_schema.event_template.registration_deadline_offset
    IS 'How long before the start registration closes, turned into the appointment''s deadline once its start is known. Empty for no deadline.';

CREATE TABLE ember_schema.event_template_federation_share
(
    id          SERIAL PRIMARY KEY,
    template_id INTEGER NOT NULL UNIQUE REFERENCES ember_schema.event_template (id) ON DELETE CASCADE,
    scope       TEXT    NOT NULL CHECK (scope IN ('ALL_PARTNERS', 'SPECIFIC'))
);

COMMENT ON TABLE ember_schema.event_template_federation_share
    IS 'Whether an appointment made from the template is shared with partner stations. No row means it is not shared.';
COMMENT ON COLUMN ember_schema.event_template_federation_share.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.event_template_federation_share.template_id IS 'The template.';
COMMENT ON COLUMN ember_schema.event_template_federation_share.scope
    IS 'ALL_PARTNERS shares with every partner, SPECIFIC with the partners listed in event_template_federation_share_target.';

CREATE TABLE ember_schema.event_template_federation_share_target
(
    share_id   INTEGER NOT NULL REFERENCES ember_schema.event_template_federation_share (id) ON DELETE CASCADE,
    partner_id INTEGER NOT NULL REFERENCES ember_schema.federation_partner (id) ON DELETE CASCADE,
    PRIMARY KEY (share_id, partner_id)
);

CREATE INDEX event_template_federation_share_target_partner_idx
    ON ember_schema.event_template_federation_share_target (partner_id);

COMMENT ON TABLE ember_schema.event_template_federation_share_target
    IS 'The partners an appointment made from the template is shared with, where the share names them.';
COMMENT ON COLUMN ember_schema.event_template_federation_share_target.share_id IS 'The template share this partner belongs to.';
COMMENT ON COLUMN ember_schema.event_template_federation_share_target.partner_id
    IS 'A partner station. Removed when the partner is deleted.';

CREATE TABLE ember_schema.event_template_attachment
(
    id          SERIAL PRIMARY KEY,
    template_id INTEGER     NOT NULL REFERENCES ember_schema.event_template (id) ON DELETE CASCADE,
    file_id     INTEGER     NOT NULL REFERENCES ember_schema.station_file (id) ON DELETE RESTRICT,
    label       TEXT,
    internal    BOOLEAN     NOT NULL DEFAULT FALSE,
    sort_order  INTEGER     NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX event_template_attachment_template_idx
    ON ember_schema.event_template_attachment (template_id, sort_order);
CREATE INDEX event_template_attachment_file_idx ON ember_schema.event_template_attachment (file_id);

COMMENT ON TABLE ember_schema.event_template_attachment
    IS 'Files of the station attached to every appointment made from the template. The same file is linked, never copied.';
COMMENT ON COLUMN ember_schema.event_template_attachment.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.event_template_attachment.template_id IS 'The template.';
COMMENT ON COLUMN ember_schema.event_template_attachment.file_id
    IS 'The file; it cannot be deleted while a template still attaches it.';
COMMENT ON COLUMN ember_schema.event_template_attachment.label IS 'The name shown instead of the file name. Empty shows the file name.';
COMMENT ON COLUMN ember_schema.event_template_attachment.internal
    IS 'True where the file is kept back from the room on the appointments made from the template: readable only with the right to read what an event keeps internal, and never sent to a partner station.';
COMMENT ON COLUMN ember_schema.event_template_attachment.sort_order IS 'Position among the template''s attachments.';
COMMENT ON COLUMN ember_schema.event_template_attachment.created_at IS 'When the file was attached.';

CREATE TABLE ember_schema.event_template_field_default
(
    template_id INTEGER NOT NULL REFERENCES ember_schema.event_template (id) ON DELETE CASCADE,
    field_id    INTEGER NOT NULL REFERENCES ember_schema.attendance_template_field (id) ON DELETE CASCADE,
    source      TEXT    NOT NULL CHECK (source IN ('VALUE', 'EVENT_NAME', 'EVENT_DESCRIPTION', 'EVENT_START_TIME',
                                                   'EVENT_END_TIME', 'EVENT_DATE')),
    value       TEXT,
    PRIMARY KEY (template_id, field_id)
);

CREATE INDEX event_template_field_default_field_idx ON ember_schema.event_template_field_default (field_id);

COMMENT ON TABLE ember_schema.event_template_field_default
    IS 'Where the fields of the attendance sheet take their values from, for appointments made from the template.';
COMMENT ON COLUMN ember_schema.event_template_field_default.template_id IS 'The template.';
COMMENT ON COLUMN ember_schema.event_template_field_default.field_id
    IS 'A field of the template''s attendance sheet. Removed when the field is deleted, which archives it, and when the template moves to another sheet.';
COMMENT ON COLUMN ember_schema.event_template_field_default.source
    IS 'Value source: VALUE, EVENT_NAME, EVENT_DESCRIPTION, EVENT_START_TIME, EVENT_END_TIME, EVENT_DATE.';
COMMENT ON COLUMN ember_schema.event_template_field_default.value IS 'The fixed value when the source is VALUE. Empty otherwise.';
