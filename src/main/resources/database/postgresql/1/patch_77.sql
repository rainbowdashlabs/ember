DROP FUNCTION IF EXISTS ember_schema.check_restriction(TEXT, TEXT, TEXT, TEXT, TEXT, INT, INT, TEXT);

CREATE OR REPLACE FUNCTION ember_schema.check_restriction(
    _restriction_table TEXT,
    _fk_column TEXT,
    _entity_table TEXT,
    _entity_id_column TEXT,
    _mode_column TEXT,
    _entity_id INT,
    _member_id INT
) RETURNS BOOLEAN
    LANGUAGE plpgsql
    STABLE
AS
$$
DECLARE
    _mode      TEXT;
    _user_type TEXT;
    _group_ids INT[];
    _tag_ids   INT[];
BEGIN
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

COMMENT ON FUNCTION ember_schema.check_restriction(TEXT, TEXT, TEXT, TEXT, TEXT, INT, INT) IS
    'Whether the restriction rows of one entity take in a member, reading the member''s user type, groups and tags and the entity''s combination mode itself. Managers are not let through here: whether somebody manages the entity type is decided by the application and combined with this result by the caller.';
