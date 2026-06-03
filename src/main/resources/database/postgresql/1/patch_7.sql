-- ============================================================
-- Role system migration: Replace flat Roles enum with two-axis
-- permission model (StationUserType + StationPermission).
-- ============================================================

-- 1. Drop DB functions that reference old role tables
DROP FUNCTION IF EXISTS ember_schema.check_restriction(TEXT, TEXT, INT, TEXT, INT, INT[], INT[], INT[]);
DROP FUNCTION IF EXISTS ember_schema.check_restriction(TEXT, TEXT, INT, TEXT, INT);
DROP FUNCTION IF EXISTS ember_schema.check_restriction(TEXT, TEXT, TEXT, TEXT, INT, INT, TEXT);
DROP TYPE IF EXISTS ember_schema.member_identity CASCADE;

-- 2. Drop old role tables (CASCADE handles FKs)
DROP TABLE IF EXISTS ember_schema.station_member_role CASCADE;
DROP TABLE IF EXISTS ember_schema.member_group_role CASCADE;
DROP TABLE IF EXISTS ember_schema.role_hierarchy CASCADE;
DROP TABLE IF EXISTS ember_schema.role CASCADE;
DROP TABLE IF EXISTS ember_schema.account_role CASCADE;

-- 3. Instance user type on account
ALTER TABLE ember_schema.account ADD COLUMN instance_user_type TEXT NOT NULL DEFAULT 'USER';

-- 4. Station user type on station_member
ALTER TABLE ember_schema.station_member ADD COLUMN user_type TEXT NOT NULL DEFAULT 'MEMBER';

-- 5. Permission tables
CREATE TABLE ember_schema.station_permission (
    id SERIAL PRIMARY KEY,
    name TEXT NOT NULL UNIQUE
);

INSERT INTO ember_schema.station_permission (name) VALUES
    ('LOGIN'), ('USER'),
    ('ATTENDANCE_CREATE'), ('ATTENDANCE_CONFIGURE'), ('ATTENDANCE_EXPORT'), ('ATTENDANCE_MANAGER'),
    ('INVENTORY_CREATE_EXTERNAL'), ('INVENTORY_CREATE_INTERNAL'), ('INVENTORY_CREATE'),
    ('INVENTORY_READ'), ('INVENTORY_PROCUREMENT'), ('INVENTORY_CHECK'),
    ('INVENTORY_LENDING_REQUEST'), ('INVENTORY_LENDING_MANAGER'), ('INVENTORY_MANAGER'),
    ('EVENT_MANAGE_TEMPLATE'), ('EVENT_MANAGE_CATEGORY'), ('EVENT_CONFIGURE'), ('EVENT_REGISTRATION'),
    ('EVENTS_FEDERATE'), ('EVENT_MANAGER'),
    ('MEMBER_READ'), ('MEMBER_NOTES'), ('MEMBER_GUARDIAN'), ('MEMBER_CHANGES'),
    ('MEMBER_MANAGE_GROUP'), ('MEMBER_MANAGE_TAGS'), ('MEMBER_EDIT'), ('MEMBER_FIELDS'), ('MEMBER_MANAGER'),
    ('WAITLIST_READ'), ('WAITLIST_EDIT'), ('WAITLIST_MANAGER'),
    ('NEWS_CREATE'), ('NEWS_FEDERATE'), ('NEWS_MANAGER'),
    ('POLL_VIEW_RESULTS'), ('POLL_CREATE'), ('POLL_MANAGER'),
    ('LOST_AND_FOUND_CREATE'), ('LOST_AND_FOUND_MANAGE'), ('LOST_AND_FOUND_MANAGER'),
    ('TEST_CATALOG_VIEW'), ('TEST_CATALOG_EDIT'), ('TEST_CONFIGURE'), ('TEST_MANAGER'),
    ('PROTOCOL_TESTER'), ('PROTOCOL_CREATE'), ('PROTOCOL_CONFIGURE'), ('PROTOCOL_MANAGER'),
    ('QUIZ_MANAGER'),
    ('BOARD_USE'), ('BOARD_EDIT'), ('BOARD_FEDERATE'), ('BOARD_MANAGER'),
    ('KNOWLEDGE_EDIT'), ('KNOWLEDGE_FEDERATE'), ('KNOWLEDGE_MANAGER'),
    ('STATION_LOOK_AND_FEEL'), ('STATION_GENERAL'), ('STATION_MAIL'),
    ('STATION_FEDERATION'), ('STATION_MODULES'), ('STATION_IMPORT_EXPORT'), ('STATION_STATISTICS'),
    ('STATION_MANAGER'), ('STATION_ADMINISTRATOR');

CREATE TABLE ember_schema.station_member_permission (
    member_id INTEGER NOT NULL REFERENCES ember_schema.station_member(id) ON DELETE CASCADE,
    permission_id INTEGER NOT NULL REFERENCES ember_schema.station_permission(id) ON DELETE CASCADE,
    PRIMARY KEY (member_id, permission_id)
);

CREATE TABLE ember_schema.member_group_permission (
    group_id INTEGER NOT NULL REFERENCES ember_schema.member_group(id) ON DELETE CASCADE,
    permission_id INTEGER NOT NULL REFERENCES ember_schema.station_permission(id) ON DELETE CASCADE,
    PRIMARY KEY (group_id, permission_id)
);

CREATE TABLE ember_schema.station_user_type_permission (
    station_id INTEGER NOT NULL REFERENCES ember_schema.station(id) ON DELETE CASCADE,
    user_type TEXT NOT NULL,
    permission_id INTEGER NOT NULL REFERENCES ember_schema.station_permission(id) ON DELETE CASCADE,
    PRIMARY KEY (station_id, user_type, permission_id)
);

-- 6. Migrate restriction tables: role_id -> user_type TEXT

-- event_restriction
ALTER TABLE ember_schema.event_restriction DROP COLUMN IF EXISTS role_id;
ALTER TABLE ember_schema.event_restriction ADD COLUMN user_type TEXT;
ALTER TABLE ember_schema.event_restriction DROP CONSTRAINT IF EXISTS event_restriction_check;
ALTER TABLE ember_schema.event_restriction ADD CONSTRAINT event_restriction_check
    CHECK (num_nonnulls(user_type, group_id, tag_id, member_id) = 1);

-- quiz_test_restriction
ALTER TABLE ember_schema.quiz_test_restriction DROP COLUMN IF EXISTS role_id;
ALTER TABLE ember_schema.quiz_test_restriction ADD COLUMN user_type TEXT;
ALTER TABLE ember_schema.quiz_test_restriction DROP CONSTRAINT IF EXISTS quiz_test_restriction_check;
ALTER TABLE ember_schema.quiz_test_restriction ADD CONSTRAINT quiz_test_restriction_check
    CHECK (num_nonnulls(user_type, group_id, tag_id, member_id) = 1);

-- form_restriction
ALTER TABLE ember_schema.form_restriction DROP COLUMN IF EXISTS role_id;
ALTER TABLE ember_schema.form_restriction ADD COLUMN user_type TEXT;
ALTER TABLE ember_schema.form_restriction DROP CONSTRAINT IF EXISTS form_restriction_check;
ALTER TABLE ember_schema.form_restriction ADD CONSTRAINT form_restriction_check
    CHECK (num_nonnulls(user_type, group_id, tag_id, member_id) = 1);

-- news_restriction
ALTER TABLE ember_schema.news_restriction DROP COLUMN IF EXISTS role_id;
ALTER TABLE ember_schema.news_restriction ADD COLUMN user_type TEXT;
ALTER TABLE ember_schema.news_restriction DROP CONSTRAINT IF EXISTS news_restriction_check;
ALTER TABLE ember_schema.news_restriction ADD CONSTRAINT news_restriction_check
    CHECK (num_nonnulls(user_type, group_id, tag_id, member_id) = 1);

-- kb_access_restriction
ALTER TABLE ember_schema.kb_access_restriction DROP COLUMN IF EXISTS role_id;
ALTER TABLE ember_schema.kb_access_restriction ADD COLUMN user_type TEXT;
ALTER TABLE ember_schema.kb_access_restriction DROP CONSTRAINT IF EXISTS kb_access_restriction_check;
ALTER TABLE ember_schema.kb_access_restriction ADD CONSTRAINT kb_access_restriction_check
    CHECK (num_nonnulls(user_type, group_id, tag_id, member_id) = 1);

-- event_template_restriction (has only role_id, add user_type)
ALTER TABLE ember_schema.event_template_restriction DROP COLUMN IF EXISTS role_id;
ALTER TABLE ember_schema.event_template_restriction ADD COLUMN user_type TEXT NOT NULL;
-- Re-create PK since role_id was part of it
ALTER TABLE ember_schema.event_template_restriction DROP CONSTRAINT IF EXISTS event_template_restriction_pkey;
ALTER TABLE ember_schema.event_template_restriction ADD PRIMARY KEY (template_id, user_type);

-- board_view_access
ALTER TABLE ember_schema.board_view_access DROP COLUMN IF EXISTS role_id;
ALTER TABLE ember_schema.board_view_access ADD COLUMN user_type TEXT;

-- board_edit_access
ALTER TABLE ember_schema.board_edit_access DROP COLUMN IF EXISTS role_id;
ALTER TABLE ember_schema.board_edit_access ADD COLUMN user_type TEXT;

-- federation_board_edit_role -> federation_board_edit_user_type
ALTER TABLE ember_schema.federation_board_edit_role RENAME TO federation_board_edit_user_type;
ALTER TABLE ember_schema.federation_board_edit_user_type DROP COLUMN IF EXISTS role_id;
ALTER TABLE ember_schema.federation_board_edit_user_type ADD COLUMN user_type TEXT NOT NULL;
ALTER TABLE ember_schema.federation_board_edit_user_type DROP CONSTRAINT IF EXISTS federation_board_edit_role_pkey;
ALTER TABLE ember_schema.federation_board_edit_user_type ADD PRIMARY KEY (board_id, user_type);

-- federation_board_local_view_override
ALTER TABLE ember_schema.federation_board_local_view_override DROP COLUMN IF EXISTS role_id;
ALTER TABLE ember_schema.federation_board_local_view_override ADD COLUMN user_type TEXT;
DROP INDEX IF EXISTS ember_schema.idx_fed_board_local_view_unique;
CREATE UNIQUE INDEX idx_fed_board_local_view_unique
    ON ember_schema.federation_board_local_view_override(partner_id, remote_board_uid, COALESCE(user_type, ''), COALESCE(group_id, -1), COALESCE(tag_id, -1));

-- federation_board_local_edit_override
ALTER TABLE ember_schema.federation_board_local_edit_override DROP COLUMN IF EXISTS role_id;
ALTER TABLE ember_schema.federation_board_local_edit_override ADD COLUMN user_type TEXT;
DROP INDEX IF EXISTS ember_schema.idx_fed_board_local_edit_unique;
CREATE UNIQUE INDEX idx_fed_board_local_edit_unique
    ON ember_schema.federation_board_local_edit_override(partner_id, remote_board_uid, COALESCE(user_type, ''), COALESCE(group_id, -1), COALESCE(tag_id, -1));

-- inventory_requirement
ALTER TABLE ember_schema.inventory_requirement DROP COLUMN IF EXISTS role_id;
ALTER TABLE ember_schema.inventory_requirement ADD COLUMN user_type TEXT;
-- Fix constraint to use user_type instead of role_id
ALTER TABLE ember_schema.inventory_requirement DROP CONSTRAINT IF EXISTS chk_role_or_group;
ALTER TABLE ember_schema.inventory_requirement ADD CONSTRAINT chk_user_type_or_group
    CHECK (user_type IS NOT NULL OR group_id IS NOT NULL);

-- 7. waiting_list.join_role_id -> join_user_type
ALTER TABLE ember_schema.waiting_list DROP COLUMN IF EXISTS join_role_id;
ALTER TABLE ember_schema.waiting_list ADD COLUMN join_user_type TEXT NOT NULL DEFAULT 'MEMBER';

-- 8. federation_board_share_target.required_role -> required_user_type
ALTER TABLE ember_schema.federation_board_share_target DROP COLUMN IF EXISTS required_role;
ALTER TABLE ember_schema.federation_board_share_target ADD COLUMN required_user_type TEXT NOT NULL DEFAULT 'MEMBER';

-- 9. Simplified check_restriction: no role hierarchy, no role expansion.
-- Manager bypass is handled in Java before calling this.
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
    LANGUAGE plpgsql STABLE
    AS $$
DECLARE
    _has_any BOOLEAN;
    _has_member_match BOOLEAN;
    _has_user_type_restrictions BOOLEAN;
    _user_type_match BOOLEAN;
    _has_group_restrictions BOOLEAN;
    _group_match BOOLEAN;
    _has_tag_restrictions BOOLEAN;
    _tag_match BOOLEAN;
BEGIN
    -- Check if any restrictions exist at all
    EXECUTE format('SELECT EXISTS(SELECT 1 FROM %I WHERE %I = $1)', _table, _fk_column)
        INTO _has_any USING _entity_id;
    IF NOT _has_any THEN RETURN TRUE; END IF;

    -- Direct member match always grants access
    EXECUTE format('SELECT EXISTS(SELECT 1 FROM %I WHERE %I = $1 AND member_id = $2)', _table, _fk_column)
        INTO _has_member_match USING _entity_id, _member_id;
    IF _has_member_match THEN RETURN TRUE; END IF;

    -- Check user_type restrictions
    EXECUTE format('SELECT EXISTS(SELECT 1 FROM %I WHERE %I = $1 AND user_type IS NOT NULL)', _table, _fk_column)
        INTO _has_user_type_restrictions USING _entity_id;
    IF _has_user_type_restrictions THEN
        EXECUTE format('SELECT EXISTS(SELECT 1 FROM %I WHERE %I = $1 AND user_type = $2)', _table, _fk_column)
            INTO _user_type_match USING _entity_id, _user_type;
    ELSE
        _user_type_match := NULL;
    END IF;

    -- Check group restrictions
    EXECUTE format('SELECT EXISTS(SELECT 1 FROM %I WHERE %I = $1 AND group_id IS NOT NULL)', _table, _fk_column)
        INTO _has_group_restrictions USING _entity_id;
    IF _has_group_restrictions THEN
        EXECUTE format('SELECT EXISTS(SELECT 1 FROM %I WHERE %I = $1 AND group_id = ANY($2))', _table, _fk_column)
            INTO _group_match USING _entity_id, _group_ids;
    ELSE
        _group_match := NULL;
    END IF;

    -- Check tag restrictions
    EXECUTE format('SELECT EXISTS(SELECT 1 FROM %I WHERE %I = $1 AND tag_id IS NOT NULL)', _table, _fk_column)
        INTO _has_tag_restrictions USING _entity_id;
    IF _has_tag_restrictions THEN
        EXECUTE format('SELECT EXISTS(SELECT 1 FROM %I WHERE %I = $1 AND tag_id = ANY($2))', _table, _fk_column)
            INTO _tag_match USING _entity_id, _tag_ids;
    ELSE
        _tag_match := NULL;
    END IF;

    -- Apply mode
    IF _mode = 'OR' THEN
        RETURN COALESCE(_user_type_match, FALSE)
            OR COALESCE(_group_match, FALSE)
            OR COALESCE(_tag_match, FALSE);
    ELSE -- AND
        RETURN COALESCE(_user_type_match, TRUE)
           AND COALESCE(_group_match, TRUE)
           AND COALESCE(_tag_match, TRUE);
    END IF;
END;
$$;

-- Convenience 7-arg wrapper that resolves member identity from DB tables
-- and checks manager permission bypass. This matches the call signature used
-- inline in repository SQL (EventRepository, NewsRepository, etc.).
CREATE OR REPLACE FUNCTION ember_schema.check_restriction(
    _restriction_table TEXT,
    _fk_column TEXT,
    _entity_table TEXT,
    _entity_id_column TEXT,
    _entity_id INT,
    _member_id INT,
    _manager_permission TEXT
) RETURNS BOOLEAN
    LANGUAGE plpgsql STABLE
    AS $$
DECLARE
    _mode TEXT;
    _user_type TEXT;
    _group_ids INT[];
    _tag_ids INT[];
    _has_manager BOOLEAN;
BEGIN
    -- Manager permission bypass
    IF _manager_permission IS NOT NULL THEN
        SELECT EXISTS(
            SELECT 1 FROM ember_schema.station_member_permission smp
            JOIN ember_schema.station_permission sp ON sp.id = smp.permission_id
            WHERE smp.member_id = _member_id AND sp.name = _manager_permission
        ) INTO _has_manager;
        IF _has_manager THEN RETURN TRUE; END IF;
    END IF;

    -- Resolve restriction mode from entity table
    EXECUTE format('SELECT restriction_mode FROM %I WHERE %I = $1', _entity_table, _entity_id_column)
        INTO _mode USING _entity_id;
    IF _mode IS NULL THEN _mode := 'AND'; END IF;

    -- Resolve member identity
    SELECT sm.user_type INTO _user_type
        FROM ember_schema.station_member sm WHERE sm.id = _member_id;
    IF _user_type IS NULL THEN RETURN FALSE; END IF;

    SELECT COALESCE(ARRAY(
        SELECT mge.group_id FROM ember_schema.member_group_entry mge WHERE mge.member_id = _member_id
    ), '{}') INTO _group_ids;

    SELECT COALESCE(ARRAY(
        SELECT ute.tag_id FROM ember_schema.user_tag_entry ute WHERE ute.member_id = _member_id
    ), '{}') INTO _tag_ids;

    RETURN ember_schema.check_restriction(
        _restriction_table, _fk_column, _entity_id, _mode, _member_id,
        _user_type, _group_ids, _tag_ids
    );
END;
$$;

-- 10. Add former_at timestamp to station_member
ALTER TABLE ember_schema.station_member ADD COLUMN IF NOT EXISTS former_at TIMESTAMPTZ;
UPDATE ember_schema.station_member SET former_at = NOW() WHERE former = TRUE AND former_at IS NULL;
