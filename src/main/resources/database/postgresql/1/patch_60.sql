-- A profile field stops carrying its audience inside itself.
--
-- The same question asked of two kinds of member used to be two rows, two ids, two configurations
-- and two answers, because the table was unique on (station_id, scope, name). A manager is shown
-- the team's questions as well as their own, so a station that wrote Geburtsdatum into both scopes
-- put it on every manager's profile twice, and each copy collected an answer of its own.
--
-- From here a field is defined once and assigned to whoever is asked it. The definition says what
-- the question is; the assignment says who is asked and how it is put to them. A cluster's fields
-- are the same idea one level up and are converted the same way.

-- ------------------------------------------------------------
-- The assignment tables.
-- ------------------------------------------------------------

CREATE TABLE ember_schema.profile_field_assignment
(
    id                SERIAL PRIMARY KEY,
    field_id          INTEGER NOT NULL REFERENCES ember_schema.profile_field (id) ON DELETE CASCADE,
    target_kind       TEXT    NOT NULL,
    role              TEXT    NULL,
    group_id          INTEGER NULL REFERENCES ember_schema.member_group (id) ON DELETE CASCADE,
    position          INTEGER NOT NULL DEFAULT 0,
    width_override    TEXT    NULL,
    readonly_override BOOLEAN NULL,
    required_override BOOLEAN NULL,

    CONSTRAINT profile_field_assignment_kind_known
        CHECK (target_kind IN ('ROLE', 'GROUP')),

    -- A row names exactly one target. Naming both would ask the question twice over, and naming
    -- neither would ask nobody while still looking like an assignment.
    CONSTRAINT profile_field_assignment_names_one_target
        CHECK ((target_kind = 'ROLE') = (role IS NOT NULL)
            AND (target_kind = 'GROUP') = (group_id IS NOT NULL))
);

CREATE UNIQUE INDEX idx_profile_field_assignment_role
    ON ember_schema.profile_field_assignment (field_id, role)
    WHERE target_kind = 'ROLE';

CREATE UNIQUE INDEX idx_profile_field_assignment_group
    ON ember_schema.profile_field_assignment (field_id, group_id)
    WHERE target_kind = 'GROUP';

CREATE INDEX idx_profile_field_assignment_field
    ON ember_schema.profile_field_assignment (field_id);

COMMENT ON TABLE ember_schema.profile_field_assignment IS
    'Who a profile field is asked of, and how it is put to them. One definition may be assigned to many roles and many groups without being written twice.';
COMMENT ON COLUMN ember_schema.profile_field_assignment.target_kind IS
    'ROLE where the field is asked of a kind of member, GROUP where it is asked of one group. Exactly one of role and group_id is set accordingly.';
COMMENT ON COLUMN ember_schema.profile_field_assignment.required_override IS
    'Whether this audience must answer, where that differs from the definition. NULL means the definition decides, which is the normal case.';
COMMENT ON COLUMN ember_schema.profile_field_assignment.width_override IS
    'How much of a row the question takes for this audience, where that differs from the definition. NULL means the definition decides. Position is not an override: where a question sits is the form''s own business and every form orders itself.';
COMMENT ON COLUMN ember_schema.profile_field_assignment.readonly_override IS
    'Whether only the member management may write the answer for this audience, where that differs from the definition. NULL means the definition decides, which is the normal case.';
COMMENT ON COLUMN ember_schema.profile_field_assignment.id IS
    'Surrogate key. The pair that names an assignment is the field and its audience, which the two partial unique indexes hold.';
COMMENT ON COLUMN ember_schema.profile_field_assignment.field_id IS
    'The question being asked. Deleting the definition takes every assignment of it.';
COMMENT ON COLUMN ember_schema.profile_field_assignment.role IS
    'The kind of member asked, set where target_kind is ROLE and NULL otherwise.';
COMMENT ON COLUMN ember_schema.profile_field_assignment.group_id IS
    'The member group asked, set where target_kind is GROUP and NULL otherwise.';
COMMENT ON COLUMN ember_schema.profile_field_assignment.position IS
    'Where the question sits on this audience''s form. Belongs to the form: two forms holding different questions cannot share one ordering.';

-- A cluster asks its questions of kinds of member too, and of a group of stations rather than of a
-- group of members: a member group belongs to one station, so a cluster cannot name one. That is
-- why station_group_id stays on the definition while the role moves out to an assignment.
CREATE TABLE ember_schema.cluster_profile_field_assignment
(
    id                SERIAL PRIMARY KEY,
    field_id          INTEGER NOT NULL REFERENCES ember_schema.cluster_profile_field (id) ON DELETE CASCADE,
    role              TEXT    NOT NULL,
    position          INTEGER NOT NULL DEFAULT 0,
    width_override    TEXT    NULL,
    readonly_override BOOLEAN NULL,
    required_override BOOLEAN NULL,

    UNIQUE (field_id, role)
);

CREATE INDEX idx_cluster_profile_field_assignment_field
    ON ember_schema.cluster_profile_field_assignment (field_id);

COMMENT ON TABLE ember_schema.cluster_profile_field_assignment IS
    'Which kinds of member a cluster question is asked of. The stations it reaches stay on the definition, because that is a different axis.';
COMMENT ON COLUMN ember_schema.cluster_profile_field_assignment.id IS
    'Surrogate key. The pair that names an assignment is the field and the role, which the unique constraint holds.';
COMMENT ON COLUMN ember_schema.cluster_profile_field_assignment.field_id IS
    'The question being asked. Deleting the definition takes every assignment of it.';
COMMENT ON COLUMN ember_schema.cluster_profile_field_assignment.role IS
    'The kind of member asked. A cluster names no member groups, so a role is the only audience it can name.';
COMMENT ON COLUMN ember_schema.cluster_profile_field_assignment.position IS
    'Where the question sits on this audience''s form. Belongs to the form: two forms holding different questions cannot share one ordering.';
COMMENT ON COLUMN ember_schema.cluster_profile_field_assignment.width_override IS
    'How much of a row the question takes for this audience, where that differs from the definition. NULL means the definition decides.';
COMMENT ON COLUMN ember_schema.cluster_profile_field_assignment.readonly_override IS
    'Whether only the member management may write the answer for this audience, where that differs from the definition. NULL means the definition decides, which is the normal case.';
COMMENT ON COLUMN ember_schema.cluster_profile_field_assignment.required_override IS
    'Whether this audience must answer, where that differs from the definition. NULL means the definition decides, which is the normal case.';

-- ------------------------------------------------------------
-- The old rows, kept before anything is merged.
--
-- The merge below discards answers where two definitions become one. A station whose data is worse
-- than expected has to be able to read back what was there, so the tables are copied first and no
-- copy is dropped by this patch.
-- ------------------------------------------------------------

-- Nothing is copied into the database. What these tables held before the merge is written to the
-- data volume by the hook that runs immediately before this patch, which is the one place a copy can
-- live without being somebody's data: a table beside the live ones is exported with a station and
-- imported into the next, and a schema of its own needs a right on the database that a role confined
-- to one schema does not have, which would strand such an instance half way through this patch.
--
-- See ProfileFieldMergeBackup.

-- ------------------------------------------------------------
-- What this patch refuses to guess at.
--
-- A field computed from another names it by string. Where such a field is part of a merge group its
-- name is about to move, and rewriting a reference held as free text is not something to do blind.
-- A field that is computed but alone in its group is untouched by the merge and passes.
-- ------------------------------------------------------------

DO
$$
    DECLARE
        blocked INTEGER;
    BEGIN
        SELECT count(*)
        INTO blocked
        FROM ember_schema.profile_field f
        WHERE ((f.config ->> 'computed')::BOOLEAN IS TRUE OR f.config ->> 'sourceField' IS NOT NULL)
          AND EXISTS (SELECT 1
                      FROM ember_schema.profile_field o
                      WHERE o.station_id = f.station_id
                        AND o.name = f.name
                        AND o.id <> f.id);

        IF blocked > 0 THEN
            RAISE EXCEPTION
                'profile_field holds % computed or source-referencing rows whose name is also used by another field. Merging would break the reference. Give them distinct names before upgrading.',
                blocked;
        END IF;
    END
$$;

-- ------------------------------------------------------------
-- Two questions that merely share a name.
--
-- Fields merge on name and type together: a text Notiz and an enum Notiz are two questions and stay
-- two definitions. They cannot both keep the name once the table is unique on it, so every group
-- after the first is renamed by its type. Deterministic, reversible from the copy on the volume, and a
-- station is free to rename it to something better afterwards.
-- ------------------------------------------------------------

DO
$$
    DECLARE
        clash RECORD;
    BEGIN
        FOR clash IN
            SELECT station_id, name, field_type, min(id) AS keeper
            FROM ember_schema.profile_field
            GROUP BY station_id, name, field_type
            LOOP
                CONTINUE WHEN (SELECT count(DISTINCT field_type)
                               FROM ember_schema.profile_field
                               WHERE station_id = clash.station_id
                                 AND name = clash.name) = 1;

                CONTINUE WHEN clash.keeper = (SELECT min(id)
                                              FROM ember_schema.profile_field
                                              WHERE station_id = clash.station_id
                                                AND name = clash.name);

                RAISE NOTICE 'profile field merge: station % has % as both % and another type; the % one is renamed',
                    clash.station_id, clash.name, clash.field_type, clash.field_type;

                UPDATE ember_schema.profile_field
                SET name = clash.name || ' (' || clash.field_type || ')'
                WHERE station_id = clash.station_id
                  AND name = clash.name
                  AND field_type = clash.field_type;
            END LOOP;
    END
$$;

-- ------------------------------------------------------------
-- Merge the station's definitions.
-- ------------------------------------------------------------

CREATE TEMP TABLE field_merge ON COMMIT DROP AS
SELECT f.id                                                             AS old_id,
       min(f.id) OVER (PARTITION BY f.station_id, f.name, f.field_type) AS new_id,
       f.scope,
       f.position,
       f.config ->> 'width'                                             AS width,
       coalesce((f.config ->> 'readonly')::BOOLEAN, FALSE)              AS readonly,
       coalesce((f.config ->> 'required')::BOOLEAN, FALSE)              AS required,
       (f.config ->> 'groupId')::INTEGER                                AS group_id
FROM ember_schema.profile_field f;

-- Every old row becomes an assignment on whichever definition survived it. A GROUP-scoped row names
-- its group in the config and becomes a group assignment; every other scope becomes a role. A
-- GROUP-scoped row that never named a group asked nobody, so it leaves the definition unassigned
-- rather than inventing an audience for it.
INSERT INTO ember_schema.profile_field_assignment
    (field_id, target_kind, role, group_id, position, width_override, readonly_override, required_override)
SELECT m.new_id,
       CASE WHEN m.scope = 'GROUP' THEN 'GROUP' ELSE 'ROLE' END,
       CASE WHEN m.scope = 'GROUP' THEN NULL ELSE m.scope END,
       CASE WHEN m.scope = 'GROUP' THEN m.group_id ELSE NULL END,
       m.position,
       -- An override says only what this audience does differently. Where it agrees with the
       -- definition it stays null, so changing the question later moves every audience that never
       -- disagreed rather than leaving them pinned to what they happened to inherit.
       CASE
           WHEN m.width IS NOT DISTINCT FROM (SELECT s.config ->> 'width'
                                              FROM ember_schema.profile_field s
                                              WHERE s.id = m.new_id) THEN NULL
           ELSE m.width
           END,
       CASE
           WHEN m.readonly = (SELECT coalesce((s.config ->> 'readonly')::BOOLEAN, FALSE)
                              FROM ember_schema.profile_field s
                              WHERE s.id = m.new_id) THEN NULL
           ELSE m.readonly
           END,
       CASE
           WHEN m.required = (SELECT coalesce((s.config ->> 'required')::BOOLEAN, FALSE)
                              FROM ember_schema.profile_field s
                              WHERE s.id = m.new_id) THEN NULL
           ELSE m.required
           END
FROM field_merge m
WHERE m.scope <> 'GROUP'
   OR m.group_id IS NOT NULL;

-- Two rules lived in code and are written down here, because the code that applied them is deleted.
-- A manager is asked the team's questions; a trial member is asked the member's. After this a
-- station can take either away, which it could not before.
INSERT INTO ember_schema.profile_field_assignment
    (field_id, target_kind, role, position, width_override, readonly_override, required_override)
SELECT a.field_id, 'ROLE', 'MANAGER', a.position, a.width_override, a.readonly_override, a.required_override
FROM ember_schema.profile_field_assignment a
WHERE a.target_kind = 'ROLE'
  AND a.role = 'TEAM'
  AND NOT EXISTS (SELECT 1
                  FROM ember_schema.profile_field_assignment b
                  WHERE b.field_id = a.field_id AND b.target_kind = 'ROLE' AND b.role = 'MANAGER');

INSERT INTO ember_schema.profile_field_assignment
    (field_id, target_kind, role, position, width_override, readonly_override, required_override)
SELECT a.field_id, 'ROLE', 'TRIAL', a.position, a.width_override, a.readonly_override, a.required_override
FROM ember_schema.profile_field_assignment a
WHERE a.target_kind = 'ROLE'
  AND a.role = 'MEMBER'
  AND NOT EXISTS (SELECT 1
                  FROM ember_schema.profile_field_assignment b
                  WHERE b.field_id = a.field_id AND b.target_kind = 'ROLE' AND b.role = 'TRIAL');

-- ------------------------------------------------------------
-- Resolve the answers.
--
-- Two value rows for one member now land on one definition. An answer that was given beats one that
-- was not, because a blank copy is what a duplicated form writes: two inputs drawn and the empty one
-- saved over nothing. Only where both are filled does the more specific role decide, and the choice
-- is announced so a station can find it afterwards.
-- ------------------------------------------------------------

DO
$$
    DECLARE
        conflict RECORD;
    BEGIN
        FOR conflict IN
            SELECT v.member_id, m.new_id, f.name, count(*) AS answers
            FROM ember_schema.profile_field_value v
                     JOIN field_merge m ON m.old_id = v.field_id
                     JOIN ember_schema.profile_field f ON f.id = m.new_id
            WHERE v.value #>> '{}' IS NOT NULL
              AND v.value #>> '{}' <> ''
            GROUP BY v.member_id, m.new_id, f.name
            HAVING count(*) > 1
            LOOP
                RAISE NOTICE
                    'profile field merge: member % gave % different answers to "%"; the most specific role wins and the rest are in the copy written to data/migrations',
                    conflict.member_id, conflict.answers, conflict.name;
            END LOOP;
    END
$$;

CREATE TEMP TABLE value_merge ON COMMIT DROP AS
SELECT DISTINCT ON (v.member_id, m.new_id) v.member_id,
                                           m.new_id AS field_id,
                                           v.value
FROM ember_schema.profile_field_value v
         JOIN field_merge m ON m.old_id = v.field_id
ORDER BY v.member_id,
         m.new_id,
         (v.value #>> '{}' IS NOT NULL AND v.value #>> '{}' <> '') DESC,
         CASE m.scope
             WHEN 'MANAGER' THEN 0
             WHEN 'TEAM' THEN 1
             WHEN 'GUARDIAN' THEN 2
             WHEN 'GROUP' THEN 3
             ELSE 4
             END,
         m.old_id;

DELETE FROM ember_schema.profile_field_value;

INSERT INTO ember_schema.profile_field_value (member_id, field_id, value)
SELECT member_id, field_id, value
FROM value_merge;

-- A member's history has to stay readable across the merge, so every recorded change follows its
-- field to the definition that survived it.
UPDATE ember_schema.profile_field_change c
SET field_id = m.new_id
FROM field_merge m
WHERE c.field_id = m.old_id
  AND m.old_id <> m.new_id;

DELETE
FROM ember_schema.profile_field f
    USING field_merge m
WHERE f.id = m.old_id
  AND m.old_id <> m.new_id;

-- ------------------------------------------------------------
-- Merge the cluster's definitions, the same way.
--
-- The group of stations a question reaches is part of what the question is here, so it joins name
-- and type in deciding what counts as the same field.
-- ------------------------------------------------------------

CREATE TEMP TABLE cluster_field_merge ON COMMIT DROP AS
SELECT f.id                                                        AS old_id,
       min(f.id) OVER (PARTITION BY f.cluster_id, f.station_group_id, f.name, f.field_type)
                                                                   AS new_id,
       f.scope,
       f.position,
       f.config ->> 'width'                                        AS width,
       coalesce((f.config ->> 'readonly')::BOOLEAN, FALSE)         AS readonly,
       coalesce((f.config ->> 'required')::BOOLEAN, FALSE)         AS required
FROM ember_schema.cluster_profile_field f;

INSERT INTO ember_schema.cluster_profile_field_assignment
    (field_id, role, position, width_override, readonly_override, required_override)
SELECT m.new_id,
       m.scope,
       m.position,
       CASE
           WHEN m.width IS NOT DISTINCT FROM (SELECT s.config ->> 'width'
                                              FROM ember_schema.cluster_profile_field s
                                              WHERE s.id = m.new_id) THEN NULL
           ELSE m.width
           END,
       CASE
           WHEN m.readonly = (SELECT coalesce((s.config ->> 'readonly')::BOOLEAN, FALSE)
                              FROM ember_schema.cluster_profile_field s
                              WHERE s.id = m.new_id) THEN NULL
           ELSE m.readonly
           END,
       CASE
           WHEN m.required = (SELECT coalesce((s.config ->> 'required')::BOOLEAN, FALSE)
                              FROM ember_schema.cluster_profile_field s
                              WHERE s.id = m.new_id) THEN NULL
           ELSE m.required
           END
FROM cluster_field_merge m
WHERE m.scope <> 'GROUP';

INSERT INTO ember_schema.cluster_profile_field_assignment
    (field_id, role, position, width_override, readonly_override, required_override)
SELECT a.field_id, 'MANAGER', a.position, a.width_override, a.readonly_override, a.required_override
FROM ember_schema.cluster_profile_field_assignment a
WHERE a.role = 'TEAM'
  AND NOT EXISTS (SELECT 1
                  FROM ember_schema.cluster_profile_field_assignment b
                  WHERE b.field_id = a.field_id AND b.role = 'MANAGER');

INSERT INTO ember_schema.cluster_profile_field_assignment
    (field_id, role, position, width_override, readonly_override, required_override)
SELECT a.field_id, 'TRIAL', a.position, a.width_override, a.readonly_override, a.required_override
FROM ember_schema.cluster_profile_field_assignment a
WHERE a.role = 'MEMBER'
  AND NOT EXISTS (SELECT 1
                  FROM ember_schema.cluster_profile_field_assignment b
                  WHERE b.field_id = a.field_id AND b.role = 'TRIAL');

CREATE TEMP TABLE cluster_value_merge ON COMMIT DROP AS
SELECT DISTINCT ON (v.member_id, m.new_id) v.member_id,
                                           m.new_id AS field_id,
                                           v.value
FROM ember_schema.cluster_profile_field_value v
         JOIN cluster_field_merge m ON m.old_id = v.field_id
ORDER BY v.member_id,
         m.new_id,
         (v.value #>> '{}' IS NOT NULL AND v.value #>> '{}' <> '') DESC,
         CASE m.scope
             WHEN 'MANAGER' THEN 0
             WHEN 'TEAM' THEN 1
             WHEN 'GUARDIAN' THEN 2
             ELSE 4
             END,
         m.old_id;

DELETE FROM ember_schema.cluster_profile_field_value;

INSERT INTO ember_schema.cluster_profile_field_value (member_id, field_id, value)
SELECT member_id, field_id, value
FROM cluster_value_merge;

UPDATE ember_schema.profile_field_change c
SET cluster_field_id = m.new_id
FROM cluster_field_merge m
WHERE c.cluster_field_id = m.old_id
  AND m.old_id <> m.new_id;

DELETE
FROM ember_schema.cluster_profile_field f
    USING cluster_field_merge m
WHERE f.id = m.old_id
  AND m.old_id <> m.new_id;

-- ------------------------------------------------------------
-- The definitions shed what is now the assignment's.
-- ------------------------------------------------------------

ALTER TABLE ember_schema.profile_field
    DROP CONSTRAINT profile_field_station_id_scope_name_key;

ALTER TABLE ember_schema.profile_field
    ADD CONSTRAINT profile_field_station_id_name_key UNIQUE (station_id, name);

-- required, readonly and width stay on the definition as columns of their own rather than keys in the
-- config, because an assignment overriding one has to be read beside it. Each is the question's own
-- answer for everybody asked it, and each audience may say otherwise for itself.
ALTER TABLE ember_schema.profile_field
    ADD COLUMN required BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN readonly BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN width    TEXT    NULL;

UPDATE ember_schema.profile_field
SET required = coalesce((config ->> 'required')::BOOLEAN, FALSE),
    readonly = coalesce((config ->> 'readonly')::BOOLEAN, FALSE),
    width    = config ->> 'width';

ALTER TABLE ember_schema.profile_field
    DROP COLUMN scope,
    DROP COLUMN position;

UPDATE ember_schema.profile_field
SET config = config - 'width' - 'readonly' - 'groupId' - 'required';

COMMENT ON COLUMN ember_schema.profile_field.required IS
    'Whether an answer is expected. An assignment may say otherwise for its own audience.';
COMMENT ON COLUMN ember_schema.profile_field.readonly IS
    'Whether only the member management may write the answer. An assignment may say otherwise for its own audience.';
COMMENT ON COLUMN ember_schema.profile_field.width IS
    'How much of a row the question takes: full, half or third. NULL is the whole row. An assignment may say otherwise for its own audience.';
COMMENT ON TABLE ember_schema.profile_field IS
    'A question a station asks about a member, defined once. Who is asked it is in profile_field_assignment.';

ALTER TABLE ember_schema.cluster_profile_field
    DROP CONSTRAINT IF EXISTS cluster_profile_field_cluster_id_scope_station_group_id_name_key;

ALTER TABLE ember_schema.cluster_profile_field
    ADD CONSTRAINT cluster_profile_field_cluster_id_group_name_key
        UNIQUE NULLS NOT DISTINCT (cluster_id, station_group_id, name);

ALTER TABLE ember_schema.cluster_profile_field
    ADD COLUMN required BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN readonly BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN width    TEXT    NULL;

UPDATE ember_schema.cluster_profile_field
SET required = coalesce((config ->> 'required')::BOOLEAN, FALSE),
    readonly = coalesce((config ->> 'readonly')::BOOLEAN, FALSE),
    width    = config ->> 'width';

ALTER TABLE ember_schema.cluster_profile_field
    DROP COLUMN scope,
    DROP COLUMN position;

UPDATE ember_schema.cluster_profile_field
SET config = config - 'width' - 'readonly' - 'groupId' - 'required';

COMMENT ON COLUMN ember_schema.cluster_profile_field.required IS
    'Whether an answer is expected. An assignment may say otherwise for its own audience.';
COMMENT ON COLUMN ember_schema.cluster_profile_field.readonly IS
    'Whether only the member management may write the answer. The question''s own, for everybody asked it. station_readonly is the other half: whether the station may write it at all.';
COMMENT ON COLUMN ember_schema.cluster_profile_field.width IS
    'How much of a row the question takes: full, half or third. NULL is the whole row. An assignment may say otherwise for its own audience.';
COMMENT ON TABLE ember_schema.cluster_profile_field IS
    'A question a cluster asks about a member, defined once. Which kinds of member are asked is in cluster_profile_field_assignment; which stations it reaches is station_group_id.';
