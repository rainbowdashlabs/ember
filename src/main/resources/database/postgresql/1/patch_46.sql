-- Who an appointment is for, and who may know it exists.
--
-- One list answered both questions until now, and answering them together meant that narrowing an
-- appointment to a group also erased it from everybody else's calendar. The common case, a drill for
-- the youth group, never meant to hide anything; the rare case, a leadership meeting, did. They get
-- a list each from here on: the one that exists keeps its rows and means registration, the new one
-- means visibility and starts out empty.
--
-- Templates carry both for the same reason they carry the title and the attendance sheet: an
-- appointment written from a template starts with what the template says, and a visibility that got
-- lost on the way would have to be set again on every date of the year.

CREATE TABLE ember_schema.event_view_restriction
(
    id        SERIAL PRIMARY KEY,
    event_id  INT NOT NULL REFERENCES ember_schema.station_event (id) ON DELETE CASCADE,
    user_type TEXT,
    group_id  INT REFERENCES ember_schema.member_group (id) ON DELETE CASCADE,
    tag_id    INT REFERENCES ember_schema.user_tag (id) ON DELETE CASCADE,
    member_id INT REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    CHECK (num_nonnulls(user_type, group_id, tag_id, member_id) = 1)
);

CREATE INDEX idx_event_view_restriction_event ON ember_schema.event_view_restriction (event_id);

COMMENT ON TABLE ember_schema.event_view_restriction IS
    'Who may know the event exists at all. Empty means everybody. Shaped like event_restriction, which says who may register.';
COMMENT ON COLUMN ember_schema.event_view_restriction.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.event_view_restriction.event_id IS 'References the station event.';
COMMENT ON COLUMN ember_schema.event_view_restriction.user_type IS
    'Required user type. Exactly one of user_type/group_id/tag_id/member_id must be set.';
COMMENT ON COLUMN ember_schema.event_view_restriction.group_id IS 'Required group membership.';
COMMENT ON COLUMN ember_schema.event_view_restriction.tag_id IS 'Required tag.';
COMMENT ON COLUMN ember_schema.event_view_restriction.member_id IS
    'Specific member (always OR-connected, bypasses AND/OR mode).';

ALTER TABLE ember_schema.station_event
    ADD COLUMN view_restriction_mode TEXT NOT NULL DEFAULT 'AND';

COMMENT ON COLUMN ember_schema.station_event.view_restriction_mode IS
    'AND or OR, how the parts of event_view_restriction combine. Separate from restriction_mode, which governs event_restriction.';

CREATE TABLE ember_schema.event_template_view_restriction
(
    id          SERIAL PRIMARY KEY,
    template_id INT NOT NULL REFERENCES ember_schema.event_template (id) ON DELETE CASCADE,
    user_type   TEXT,
    group_id    INT REFERENCES ember_schema.member_group (id) ON DELETE CASCADE,
    tag_id      INT REFERENCES ember_schema.user_tag (id) ON DELETE CASCADE,
    member_id   INT REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    CHECK (num_nonnulls(user_type, group_id, tag_id, member_id) = 1)
);

CREATE INDEX idx_event_template_view_restriction_template
    ON ember_schema.event_template_view_restriction (template_id);

COMMENT ON TABLE ember_schema.event_template_view_restriction IS
    'The visibility an appointment written from this template starts with. Not a lock on the template: nobody attends a template.';
COMMENT ON COLUMN ember_schema.event_template_view_restriction.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.event_template_view_restriction.template_id IS 'References the event template.';
COMMENT ON COLUMN ember_schema.event_template_view_restriction.user_type IS
    'Required user type. Exactly one of user_type/group_id/tag_id/member_id must be set.';
COMMENT ON COLUMN ember_schema.event_template_view_restriction.group_id IS 'Required group membership.';
COMMENT ON COLUMN ember_schema.event_template_view_restriction.tag_id IS 'Required tag.';
COMMENT ON COLUMN ember_schema.event_template_view_restriction.member_id IS 'Specific member.';

ALTER TABLE ember_schema.event_template
    ADD COLUMN view_restriction_mode TEXT NOT NULL DEFAULT 'AND';

COMMENT ON COLUMN ember_schema.event_template.view_restriction_mode IS
    'AND or OR, how the parts of event_template_view_restriction combine. Separate from restriction_mode.';

-- The wrapper read the combination mode from a column it named itself, so an entity could only ever
-- carry one restriction list. It takes the column as an argument now.
DROP FUNCTION IF EXISTS ember_schema.check_restriction(TEXT, TEXT, TEXT, TEXT, INT, INT, TEXT);

CREATE OR REPLACE FUNCTION ember_schema.check_restriction(
    _restriction_table TEXT,
    _fk_column TEXT,
    _entity_table TEXT,
    _entity_id_column TEXT,
    _mode_column TEXT,
    _entity_id INT,
    _member_id INT,
    _manager_permission TEXT
) RETURNS BOOLEAN
    LANGUAGE plpgsql
    STABLE
AS
$$
DECLARE
    _mode        TEXT;
    _user_type   TEXT;
    _group_ids   INT[];
    _tag_ids     INT[];
    _has_manager BOOLEAN;
BEGIN
    IF _manager_permission IS NOT NULL THEN
        SELECT EXISTS(SELECT 1
                      FROM ember_schema.station_member_permission smp
                               JOIN ember_schema.station_permission sp ON sp.id = smp.permission_id
                      WHERE smp.member_id = _member_id
                        AND sp.name = _manager_permission)
        INTO _has_manager;
        IF _has_manager THEN RETURN TRUE; END IF;
    END IF;

    EXECUTE format('SELECT %I FROM %I WHERE %I = $1', _mode_column, _entity_table, _entity_id_column)
        INTO _mode USING _entity_id;
    IF _mode IS NULL THEN _mode := 'AND'; END IF;

    SELECT sm.user_type INTO _user_type FROM ember_schema.station_member sm WHERE sm.id = _member_id;
    IF _user_type IS NULL THEN RETURN FALSE; END IF;

    SELECT COALESCE(ARRAY(SELECT mge.group_id
                          FROM ember_schema.member_group_entry mge
                          WHERE mge.member_id = _member_id), '{}')
    INTO _group_ids;

    SELECT COALESCE(ARRAY(SELECT ute.tag_id
                          FROM ember_schema.user_tag_entry ute
                          WHERE ute.member_id = _member_id), '{}')
    INTO _tag_ids;

    RETURN ember_schema.check_restriction(
            _restriction_table, _fk_column, _entity_id, _mode, _member_id,
            _user_type, _group_ids, _tag_ids
           );
END;
$$;
