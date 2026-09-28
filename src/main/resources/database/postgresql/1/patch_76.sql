CREATE OR REPLACE FUNCTION ember_schema.check_restriction(
    _table TEXT,
    _fk_column TEXT,
    _entity_id INT,
    _mode TEXT,
    _member_id INT,
    _user_type TEXT,
    _group_ids INT[],
    _tag_ids INT[]
) RETURNS BOOLEAN
    LANGUAGE plpgsql
    STABLE
AS
$$
DECLARE
    _has_any                    BOOLEAN;
    _has_member_match           BOOLEAN;
    _has_user_type_restrictions BOOLEAN;
    _user_type_match            BOOLEAN;
    _has_group_restrictions     BOOLEAN;
    _group_match                BOOLEAN;
    _has_tag_restrictions       BOOLEAN;
    _tag_match                  BOOLEAN;
BEGIN
    EXECUTE format('SELECT EXISTS(SELECT 1 FROM %I WHERE %I = $1)', _table, _fk_column)
        INTO _has_any USING _entity_id;
    IF NOT _has_any THEN RETURN TRUE; END IF;

    EXECUTE format('SELECT EXISTS(SELECT 1 FROM %I WHERE %I = $1 AND member_id = $2)', _table, _fk_column)
        INTO _has_member_match USING _entity_id, _member_id;
    IF _has_member_match THEN RETURN TRUE; END IF;

    EXECUTE format('SELECT EXISTS(SELECT 1 FROM %I WHERE %I = $1 AND user_type IS NOT NULL)', _table, _fk_column)
        INTO _has_user_type_restrictions USING _entity_id;
    EXECUTE format('SELECT EXISTS(SELECT 1 FROM %I WHERE %I = $1 AND group_id IS NOT NULL)', _table, _fk_column)
        INTO _has_group_restrictions USING _entity_id;
    EXECUTE format('SELECT EXISTS(SELECT 1 FROM %I WHERE %I = $1 AND tag_id IS NOT NULL)', _table, _fk_column)
        INTO _has_tag_restrictions USING _entity_id;

    IF NOT (_has_user_type_restrictions OR _has_group_restrictions OR _has_tag_restrictions) THEN
        RETURN FALSE;
    END IF;

    IF _has_user_type_restrictions THEN
        EXECUTE format('SELECT EXISTS(SELECT 1 FROM %I WHERE %I = $1 AND user_type = $2)', _table, _fk_column)
            INTO _user_type_match USING _entity_id, _user_type;
    END IF;

    IF _has_group_restrictions THEN
        EXECUTE format('SELECT EXISTS(SELECT 1 FROM %I WHERE %I = $1 AND group_id = ANY($2))', _table, _fk_column)
            INTO _group_match USING _entity_id, _group_ids;
    END IF;

    IF _has_tag_restrictions THEN
        EXECUTE format('SELECT EXISTS(SELECT 1 FROM %I WHERE %I = $1 AND tag_id = ANY($2))', _table, _fk_column)
            INTO _tag_match USING _entity_id, _tag_ids;
    END IF;

    IF _mode = 'OR' THEN
        RETURN COALESCE(_user_type_match, FALSE)
            OR COALESCE(_group_match, FALSE)
            OR COALESCE(_tag_match, FALSE);
    END IF;

    RETURN COALESCE(_user_type_match, TRUE)
       AND COALESCE(_group_match, TRUE)
       AND COALESCE(_tag_match, TRUE);
END;
$$;

COMMENT ON FUNCTION ember_schema.check_restriction(TEXT, TEXT, INT, TEXT, INT, TEXT, INT[], INT[]) IS
    'Whether a member passes the restriction rows of one entity. No rows lets everybody in, and a member named in a row always gets in. Otherwise the user type, group and tag rows decide, combined by the mode (AND or OR); where the only rows name members, everybody not named is kept out.';
