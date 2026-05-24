-- Add source reference for files copied from federation partners
ALTER TABLE ember_schema.kb_file
    ADD COLUMN source_file_id    INT DEFAULT NULL,
    ADD COLUMN source_station_id INT DEFAULT NULL;

-- Add UUID identifier to station for external use (avoids enumeration, enables cross-instance federation)
ALTER TABLE ember_schema.station
    ADD COLUMN uid UUID NOT NULL DEFAULT gen_random_uuid();
CREATE UNIQUE INDEX idx_station_uid ON ember_schema.station(uid);

-- Public knowledgebase mode per station (OFF, ALLOW_ALL, DENY_ALL)
ALTER TABLE ember_schema.station
    ADD COLUMN public_kb_mode TEXT NOT NULL DEFAULT 'OFF';

-- Per-folder/file public visibility override
CREATE TABLE ember_schema.kb_public_visibility (
    id        SERIAL PRIMARY KEY,
    folder_id INT UNIQUE REFERENCES ember_schema.kb_folder(id) ON DELETE CASCADE,
    file_id   INT UNIQUE REFERENCES ember_schema.kb_file(id) ON DELETE CASCADE,
    visible   BOOLEAN NOT NULL,
    CONSTRAINT chk_kb_public_vis_target CHECK (
        (folder_id IS NOT NULL AND file_id IS NULL)
        OR (folder_id IS NULL AND file_id IS NOT NULL)
    )
);

-- Add missing roles that were only in Java enum but not in the DB
INSERT INTO ember_schema.role (name) VALUES
    ('QUIZ_MANAGEMENT'),
    ('KNOWLEDGE_MANAGEMENT'),
    ('FEDERATION_MANAGEMENT'),
    ('PROTOCOL_MANAGEMENT'),
    ('PROTOCOL_TESTER'),
    ('TRIAL')
    ON CONFLICT (name) DO NOTHING;

-- Rename management roles to manager (fix typo in ATTENDENCE too)
UPDATE ember_schema.role SET name = 'ATTENDANCE_MANAGER' WHERE name = 'ATTENDENCE_MANAGEMENT';
UPDATE ember_schema.role SET name = 'ATTENDANCE_EXPORT_MANAGER' WHERE name = 'ATTENDENCE_EXPORT_MANAGER';
UPDATE ember_schema.role SET name = 'INVENTORY_MANAGER' WHERE name = 'INVENTORY_MANAGEMENT';
UPDATE ember_schema.role SET name = 'EVENT_MANAGER' WHERE name = 'EVENT_MANAGEMENT';
UPDATE ember_schema.role SET name = 'MEMBER_MANAGER' WHERE name = 'MEMBER_MANAGEMENT';
UPDATE ember_schema.role SET name = 'NEWS_MANAGER' WHERE name = 'NEWS_MANAGEMENT';
UPDATE ember_schema.role SET name = 'POLL_MANAGER' WHERE name = 'POLL_MANAGEMENT';
UPDATE ember_schema.role SET name = 'LOST_AND_FOUND_MANAGER' WHERE name = 'LOST_AND_FOUND_MANAGEMENT';
UPDATE ember_schema.role SET name = 'WAITLIST_MANAGER' WHERE name = 'WAITLIST_MANAGEMENT';
UPDATE ember_schema.role SET name = 'QUIZ_MANAGER' WHERE name = 'QUIZ_MANAGEMENT';
UPDATE ember_schema.role SET name = 'KNOWLEDGE_MANAGER' WHERE name = 'KNOWLEDGE_MANAGEMENT';
UPDATE ember_schema.role SET name = 'FEDERATION_MANAGER' WHERE name = 'FEDERATION_MANAGEMENT';
UPDATE ember_schema.role SET name = 'PROTOCOL_MANAGER' WHERE name = 'PROTOCOL_MANAGEMENT';

-- API request log for monitoring
CREATE TABLE ember_schema.api_request_log (
    id          BIGSERIAL PRIMARY KEY,
    method      TEXT      NOT NULL,
    path        TEXT      NOT NULL,
    status_code INT       NOT NULL,
    duration_ms INT       NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_api_request_log_created ON ember_schema.api_request_log(created_at);
CREATE INDEX idx_api_request_log_path ON ember_schema.api_request_log(path, created_at);

-- Add private key storage for federation request signing (per station, reused for all partners)
ALTER TABLE ember_schema.station ADD COLUMN federation_private_key TEXT;

-- Federation partner: add remote_host for cross-instance communication (NULL = local/same instance)
ALTER TABLE ember_schema.federation_partner ADD COLUMN remote_host TEXT;

-- Federation partner: add webhook and sync columns
ALTER TABLE ember_schema.federation_partner ADD COLUMN webhook_url TEXT;
ALTER TABLE ember_schema.federation_partner ADD COLUMN last_sync_at TIMESTAMP;

-- Federation change log for sync polling
CREATE TABLE ember_schema.federation_change_log (
    id          SERIAL PRIMARY KEY,
    station_id  INT       NOT NULL REFERENCES ember_schema.station(id) ON DELETE CASCADE,
    content_type TEXT     NOT NULL,
    content_id  INT       NOT NULL,
    change_type TEXT      NOT NULL,
    changed_at  TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_federation_change_log_station ON ember_schema.federation_change_log(station_id, changed_at);

-- Restriction mode (AND/OR) for entities with role/group/tag restrictions
ALTER TABLE ember_schema.station_event ADD COLUMN restriction_mode TEXT NOT NULL DEFAULT 'AND';
ALTER TABLE ember_schema.quiz_test ADD COLUMN restriction_mode TEXT NOT NULL DEFAULT 'AND';
ALTER TABLE ember_schema.form ADD COLUMN restriction_mode TEXT NOT NULL DEFAULT 'AND';
ALTER TABLE ember_schema.news ADD COLUMN restriction_mode TEXT NOT NULL DEFAULT 'AND';

-- ============================================================
-- Unified restriction tables (replace per-type restriction tables)
-- Each entity gets one restriction table with role/group/tag/member columns.
-- Exactly one of role_id, group_id, tag_id, member_id must be set per row.
-- ============================================================

-- Events
CREATE TABLE ember_schema.event_restriction (
    id        SERIAL PRIMARY KEY,
    event_id  INT NOT NULL REFERENCES ember_schema.station_event(id) ON DELETE CASCADE,
    role_id   INT REFERENCES ember_schema.role(id) ON DELETE CASCADE,
    group_id  INT REFERENCES ember_schema.member_group(id) ON DELETE CASCADE,
    tag_id    INT REFERENCES ember_schema.user_tag(id) ON DELETE CASCADE,
    member_id INT REFERENCES ember_schema.station_member(id) ON DELETE CASCADE,
    CHECK (num_nonnulls(role_id, group_id, tag_id, member_id) = 1)
);
CREATE INDEX idx_event_restriction_event ON ember_schema.event_restriction(event_id);

-- Migrate existing event restriction data
INSERT INTO ember_schema.event_restriction (event_id, role_id)
    SELECT event_id, role_id FROM ember_schema.event_role_restriction;
INSERT INTO ember_schema.event_restriction (event_id, group_id)
    SELECT event_id, group_id FROM ember_schema.event_group_restriction;
INSERT INTO ember_schema.event_restriction (event_id, tag_id)
    SELECT event_id, tag_id FROM ember_schema.event_tag_restriction;

DROP TABLE ember_schema.event_tag_restriction;
DROP TABLE ember_schema.event_group_restriction;
DROP TABLE ember_schema.event_role_restriction;

-- Quiz Tests
CREATE TABLE ember_schema.quiz_test_restriction (
    id        SERIAL PRIMARY KEY,
    test_id   INT NOT NULL REFERENCES ember_schema.quiz_test(id) ON DELETE CASCADE,
    role_id   INT REFERENCES ember_schema.role(id) ON DELETE CASCADE,
    group_id  INT REFERENCES ember_schema.member_group(id) ON DELETE CASCADE,
    tag_id    INT REFERENCES ember_schema.user_tag(id) ON DELETE CASCADE,
    member_id INT REFERENCES ember_schema.station_member(id) ON DELETE CASCADE,
    CHECK (num_nonnulls(role_id, group_id, tag_id, member_id) = 1)
);
CREATE INDEX idx_quiz_test_restriction_test ON ember_schema.quiz_test_restriction(test_id);

-- Migrate existing quiz test restriction data
INSERT INTO ember_schema.quiz_test_restriction (test_id, role_id)
    SELECT test_id, role_id FROM ember_schema.quiz_test_role_restriction;
INSERT INTO ember_schema.quiz_test_restriction (test_id, group_id)
    SELECT test_id, group_id FROM ember_schema.quiz_test_group_restriction;
INSERT INTO ember_schema.quiz_test_restriction (test_id, tag_id)
    SELECT test_id, tag_id FROM ember_schema.quiz_test_tag_restriction;

DROP TABLE ember_schema.quiz_test_tag_restriction;
DROP TABLE ember_schema.quiz_test_group_restriction;
DROP TABLE ember_schema.quiz_test_role_restriction;

-- Forms
CREATE TABLE ember_schema.form_restriction (
    id        SERIAL PRIMARY KEY,
    form_id   INT NOT NULL REFERENCES ember_schema.form(id) ON DELETE CASCADE,
    role_id   INT REFERENCES ember_schema.role(id) ON DELETE CASCADE,
    group_id  INT REFERENCES ember_schema.member_group(id) ON DELETE CASCADE,
    tag_id    INT REFERENCES ember_schema.user_tag(id) ON DELETE CASCADE,
    member_id INT REFERENCES ember_schema.station_member(id) ON DELETE CASCADE,
    CHECK (num_nonnulls(role_id, group_id, tag_id, member_id) = 1)
);
CREATE INDEX idx_form_restriction_form ON ember_schema.form_restriction(form_id);

-- Migrate existing form restriction data
INSERT INTO ember_schema.form_restriction (form_id, role_id)
    SELECT form_id, role_id FROM ember_schema.form_role_restriction;
INSERT INTO ember_schema.form_restriction (form_id, group_id)
    SELECT form_id, group_id FROM ember_schema.form_group_restriction;
INSERT INTO ember_schema.form_restriction (form_id, tag_id)
    SELECT form_id, tag_id FROM ember_schema.form_tag_restriction;

DROP TABLE ember_schema.form_tag_restriction;
DROP TABLE ember_schema.form_group_restriction;
DROP TABLE ember_schema.form_role_restriction;

-- News
CREATE TABLE ember_schema.news_restriction (
    id        SERIAL PRIMARY KEY,
    news_id   INT NOT NULL REFERENCES ember_schema.news(id) ON DELETE CASCADE,
    role_id   INT REFERENCES ember_schema.role(id) ON DELETE CASCADE,
    group_id  INT REFERENCES ember_schema.member_group(id) ON DELETE CASCADE,
    tag_id    INT REFERENCES ember_schema.user_tag(id) ON DELETE CASCADE,
    member_id INT REFERENCES ember_schema.station_member(id) ON DELETE CASCADE,
    CHECK (num_nonnulls(role_id, group_id, tag_id, member_id) = 1)
);
CREATE INDEX idx_news_restriction_news ON ember_schema.news_restriction(news_id);

-- Migrate existing news group restriction data
INSERT INTO ember_schema.news_restriction (news_id, group_id)
    SELECT news_id, group_id FROM ember_schema.news_group_restriction;

DROP TABLE ember_schema.news_group_restriction;

-- KB (already has unified kb_access_restriction table — add restriction_mode to folders/files)
ALTER TABLE ember_schema.kb_folder ADD COLUMN restriction_mode TEXT NOT NULL DEFAULT 'AND';
ALTER TABLE ember_schema.kb_file ADD COLUMN restriction_mode TEXT NOT NULL DEFAULT 'AND';

-- ============================================================
-- Restriction check function
-- Checks whether a member passes the restrictions in a given table.
-- Member restrictions (member_id) are always OR-connected.
-- Role/group/tag restrictions respect the given mode (AND/OR).
-- Returns TRUE if no restrictions exist or if the member matches.
-- ============================================================
CREATE OR REPLACE FUNCTION ember_schema.check_restriction(
    _table TEXT,
    _fk_column TEXT,
    _entity_id INT,
    _mode TEXT,             -- 'AND' or 'OR'
    _member_id INT,         -- the member to check
    _role_ids INT[],        -- member's role IDs
    _group_ids INT[],       -- member's group IDs
    _tag_ids INT[]          -- member's tag IDs
) RETURNS BOOLEAN
    LANGUAGE plpgsql STABLE
    AS $$
DECLARE
    _has_any BOOLEAN;
    _has_member_match BOOLEAN;
    _has_role_restrictions BOOLEAN;
    _has_group_restrictions BOOLEAN;
    _has_tag_restrictions BOOLEAN;
    _has_non_member BOOLEAN;
    _role_match BOOLEAN;
    _group_match BOOLEAN;
    _tag_match BOOLEAN;
BEGIN
    -- Check if any restrictions exist
    EXECUTE format('SELECT EXISTS(SELECT 1 FROM %I WHERE %I = $1)', _table, _fk_column)
        INTO _has_any USING _entity_id;
    IF NOT _has_any THEN RETURN TRUE; END IF;

    -- Check per-member restrictions (always OR)
    EXECUTE format('SELECT EXISTS(SELECT 1 FROM %I WHERE %I = $1 AND member_id = $2)', _table, _fk_column)
        INTO _has_member_match USING _entity_id, _member_id;
    IF _has_member_match THEN RETURN TRUE; END IF;

    -- Check if there are non-member restrictions
    EXECUTE format('SELECT EXISTS(SELECT 1 FROM %I WHERE %I = $1 AND member_id IS NULL)', _table, _fk_column)
        INTO _has_non_member USING _entity_id;
    IF NOT _has_non_member THEN RETURN FALSE; END IF;

    -- Check each type
    EXECUTE format('SELECT EXISTS(SELECT 1 FROM %I WHERE %I = $1 AND role_id IS NOT NULL)', _table, _fk_column)
        INTO _has_role_restrictions USING _entity_id;
    EXECUTE format('SELECT EXISTS(SELECT 1 FROM %I WHERE %I = $1 AND group_id IS NOT NULL)', _table, _fk_column)
        INTO _has_group_restrictions USING _entity_id;
    EXECUTE format('SELECT EXISTS(SELECT 1 FROM %I WHERE %I = $1 AND tag_id IS NOT NULL)', _table, _fk_column)
        INTO _has_tag_restrictions USING _entity_id;

    _role_match := NOT _has_role_restrictions;
    _group_match := NOT _has_group_restrictions;
    _tag_match := NOT _has_tag_restrictions;

    IF _has_role_restrictions THEN
        EXECUTE format('SELECT EXISTS(SELECT 1 FROM %I WHERE %I = $1 AND role_id = ANY($2))', _table, _fk_column)
            INTO _role_match USING _entity_id, _role_ids;
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
        RETURN _role_match OR _group_match OR _tag_match;
    ELSE
        RETURN _role_match AND _group_match AND _tag_match;
    END IF;
END;
$$;

-- ============================================================
-- Role hierarchy for restriction resolution.
-- parent_role_id includes child_role_id (e.g., MANAGER includes TEAM).
-- ============================================================
CREATE TABLE ember_schema.role_hierarchy (
    parent_role_id INT NOT NULL REFERENCES ember_schema.role(id) ON DELETE CASCADE,
    child_role_id  INT NOT NULL REFERENCES ember_schema.role(id) ON DELETE CASCADE,
    PRIMARY KEY (parent_role_id, child_role_id)
);

-- Populate hierarchy (transitive closure of role inclusions)
-- MANAGER includes TEAM + all *_MANAGER roles + PROTOCOL_TESTER
-- TEAM includes LOGIN
-- GUARDIAN includes LOGIN
-- TRIAL includes LOGIN
-- Note: USER is a Java-only role, not stored in the DB role table
INSERT INTO ember_schema.role_hierarchy (parent_role_id, child_role_id)
SELECT p.id, c.id FROM ember_schema.role p, ember_schema.role c
WHERE (p.name, c.name) IN (
    -- TEAM includes LOGIN
    ('TEAM', 'LOGIN'),
    -- GUARDIAN includes LOGIN
    ('GUARDIAN', 'LOGIN'),
    -- TRIAL includes LOGIN
    ('TRIAL', 'LOGIN'),
    -- MANAGER includes everything
    ('MANAGER', 'TEAM'),
    ('MANAGER', 'LOGIN'),
    ('MANAGER', 'ATTENDANCE_MANAGER'),
    ('MANAGER', 'ATTENDANCE_EXPORT_MANAGER'),
    ('MANAGER', 'INVENTORY_MANAGER'),
    ('MANAGER', 'EVENT_MANAGER'),
    ('MANAGER', 'MEMBER_MANAGER'),
    ('MANAGER', 'NEWS_MANAGER'),
    ('MANAGER', 'POLL_MANAGER'),
    ('MANAGER', 'LOST_AND_FOUND_MANAGER'),
    ('MANAGER', 'WAITLIST_MANAGER'),
    ('MANAGER', 'QUIZ_MANAGER'),
    ('MANAGER', 'KNOWLEDGE_MANAGER'),
    ('MANAGER', 'PROTOCOL_MANAGER'),
    ('MANAGER', 'PROTOCOL_TESTER'),
    ('MANAGER', 'FEDERATION_MANAGER')
) ON CONFLICT DO NOTHING;

-- ============================================================
-- Resolve member identity (roles, groups, tags) for restriction checks.
-- Returns a composite type. Separating this allows the DB to cache the result
-- when checking multiple entities for the same member in one query.
-- ============================================================
CREATE TYPE ember_schema.member_identity AS (
    role_ids  INT[],
    group_ids INT[],
    tag_ids   INT[]
);

CREATE OR REPLACE FUNCTION ember_schema.resolve_member_identity(_member_id INT)
RETURNS ember_schema.member_identity
    LANGUAGE sql STABLE
    AS $$
    SELECT
        -- Expand roles via hierarchy: direct roles + all inherited child roles
        COALESCE(ARRAY(
            SELECT DISTINCT r.role_id FROM (
                -- Direct roles
                SELECT role_id FROM ember_schema.station_member_role WHERE member_id = _member_id
                UNION
                -- Inherited roles (parent role grants child roles)
                SELECT h.child_role_id FROM ember_schema.station_member_role smr
                    JOIN ember_schema.role_hierarchy h ON h.parent_role_id = smr.role_id
                    WHERE smr.member_id = _member_id
            ) r
        ), '{}'),
        COALESCE(ARRAY(SELECT group_id FROM ember_schema.member_group_entry WHERE member_id = _member_id), '{}'),
        COALESCE(ARRAY(SELECT tag_id FROM ember_schema.user_tag_entry WHERE member_id = _member_id), '{}')
$$;

-- Simplified check_restriction that resolves member identity automatically.
-- Mode is still passed explicitly since it lives on different entity tables.
CREATE OR REPLACE FUNCTION ember_schema.check_restriction(
    _table TEXT,
    _fk_column TEXT,
    _entity_id INT,
    _mode TEXT,
    _member_id INT
) RETURNS BOOLEAN
    LANGUAGE plpgsql STABLE
    AS $$
DECLARE
    _identity ember_schema.member_identity;
BEGIN
    _identity := ember_schema.resolve_member_identity(_member_id);
    RETURN ember_schema.check_restriction(
        _table, _fk_column, _entity_id, _mode, _member_id,
        _identity.role_ids, _identity.group_ids, _identity.tag_ids
    );
END;
$$;

-- Fully autonomous check_restriction that resolves member identity, mode, AND manager bypass.
-- If the member has the management role for this entity type, returns TRUE immediately.
CREATE OR REPLACE FUNCTION ember_schema.check_restriction(
    _restriction_table TEXT,  -- e.g. 'event_restriction'
    _fk_column TEXT,          -- e.g. 'event_id'
    _entity_table TEXT,       -- e.g. 'station_event'
    _entity_id_column TEXT,   -- e.g. 'id'
    _entity_id INT,
    _member_id INT,
    _manager_role TEXT        -- e.g. 'EVENT_MANAGER' — if member has this role, bypass restrictions
) RETURNS BOOLEAN
    LANGUAGE plpgsql STABLE
    AS $$
DECLARE
    _mode TEXT;
    _identity ember_schema.member_identity;
    _has_manager_role BOOLEAN;
BEGIN
    -- Check manager bypass: if member has the management role (with inheritance), skip restrictions
    IF _manager_role IS NOT NULL THEN
        SELECT EXISTS(
            SELECT 1 FROM ember_schema.station_member_role smr
            JOIN ember_schema.role r ON r.id = smr.role_id
            WHERE smr.member_id = _member_id AND r.name = _manager_role
            UNION
            SELECT 1 FROM ember_schema.station_member_role smr
            JOIN ember_schema.role_hierarchy h ON h.parent_role_id = smr.role_id
            JOIN ember_schema.role r ON r.id = h.child_role_id
            WHERE smr.member_id = _member_id AND r.name = _manager_role
        ) INTO _has_manager_role;
        IF _has_manager_role THEN RETURN TRUE; END IF;
    END IF;

    -- Resolve mode from entity table
    EXECUTE format('SELECT restriction_mode FROM %I WHERE %I = $1', _entity_table, _entity_id_column)
        INTO _mode USING _entity_id;
    IF _mode IS NULL THEN _mode := 'AND'; END IF;

    _identity := ember_schema.resolve_member_identity(_member_id);
    RETURN ember_schema.check_restriction(
        _restriction_table, _fk_column, _entity_id, _mode, _member_id,
        _identity.role_ids, _identity.group_ids, _identity.tag_ids
    );
END;
$$;
