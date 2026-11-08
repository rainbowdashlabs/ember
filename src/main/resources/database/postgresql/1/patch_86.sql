-- Time somebody spends on an appointment before it starts and after it ends.
--
-- A youth leader who sets up the room or a trainer who prepares the exercise works before the sheet
-- begins, and somebody who tidies the vehicle works after it ends. That time belongs in the hours a
-- report adds up, but it is not when they were there, so it is not written into their check-in or
-- check-out. A template names who does such work, by the same restriction every other restricted
-- feature uses, and how many minutes before and after it is worth. A sheet copies those rules when it
-- is made and owns them from then on, so changing a template later does not rewrite past reports and
-- one evening can be corrected on its own sheet.

CREATE TABLE ember_schema.attendance_template_time_rule
(
    id                  SERIAL PRIMARY KEY,
    template_id         INT     NOT NULL REFERENCES ember_schema.attendance_template (id) ON DELETE CASCADE,
    position            INT     NOT NULL,
    label               TEXT,
    preparation_minutes INT     NOT NULL DEFAULT 0 CHECK (preparation_minutes BETWEEN 0 AND 1440),
    follow_up_minutes   INT     NOT NULL DEFAULT 0 CHECK (follow_up_minutes BETWEEN 0 AND 1440),
    restriction_mode    TEXT    NOT NULL DEFAULT 'AND' CHECK (restriction_mode IN ('AND', 'OR')),
    named_kinds         INT     NOT NULL DEFAULT 0 CHECK (named_kinds >= 0)
);

CREATE INDEX idx_attendance_template_time_rule_template
    ON ember_schema.attendance_template_time_rule (template_id, position);

COMMENT ON TABLE ember_schema.attendance_template_time_rule IS
    'Preparation and follow-up time a template grants to the people it names. Copied onto every sheet made from the template; a sheet never reads these rows again.';
COMMENT ON COLUMN ember_schema.attendance_template_time_rule.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.attendance_template_time_rule.template_id IS
    'The template the rule belongs to. Deleting the template deletes its rules.';
COMMENT ON COLUMN ember_schema.attendance_template_time_rule.position IS
    'Where the rule stands in the template''s list, starting at 0.';
COMMENT ON COLUMN ember_schema.attendance_template_time_rule.label IS
    'What the rule is for, such as a role name, shown beside the minutes. NULL where nobody named it.';
COMMENT ON COLUMN ember_schema.attendance_template_time_rule.preparation_minutes IS
    'Minutes of preparation a present person the rule names is credited with.';
COMMENT ON COLUMN ember_schema.attendance_template_time_rule.follow_up_minutes IS
    'Minutes of follow-up a present person the rule names is credited with.';
COMMENT ON COLUMN ember_schema.attendance_template_time_rule.restriction_mode IS
    'How the kinds named in attendance_template_time_rule_restriction combine: AND needs a match of every kind named, OR one of any. A rule naming nobody applies to nobody.';
COMMENT ON COLUMN ember_schema.attendance_template_time_rule.named_kinds IS
    'How many user types, groups and tags the rule named when it was written. A deleted group or tag removes its row from attendance_template_time_rule_restriction; a rule with fewer such rows than this credits nobody, so a deletion never widens it to people it did not name.';

CREATE TABLE ember_schema.attendance_template_time_rule_restriction
(
    id        SERIAL PRIMARY KEY,
    rule_id   INT NOT NULL REFERENCES ember_schema.attendance_template_time_rule (id) ON DELETE CASCADE,
    user_type TEXT,
    group_id  INT REFERENCES ember_schema.member_group (id) ON DELETE CASCADE,
    tag_id    INT REFERENCES ember_schema.user_tag (id) ON DELETE CASCADE,
    member_id INT REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    CHECK (num_nonnulls(user_type, group_id, tag_id, member_id) = 1)
);

CREATE INDEX idx_attendance_template_time_rule_restriction_rule
    ON ember_schema.attendance_template_time_rule_restriction (rule_id);

COMMENT ON TABLE ember_schema.attendance_template_time_rule_restriction IS
    'Whom a template time rule names. Shaped like event_restriction.';
COMMENT ON COLUMN ember_schema.attendance_template_time_rule_restriction.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.attendance_template_time_rule_restriction.rule_id IS 'References the rule.';
COMMENT ON COLUMN ember_schema.attendance_template_time_rule_restriction.user_type IS
    'Required user type. Exactly one of user_type/group_id/tag_id/member_id must be set.';
COMMENT ON COLUMN ember_schema.attendance_template_time_rule_restriction.group_id IS 'Required group membership.';
COMMENT ON COLUMN ember_schema.attendance_template_time_rule_restriction.tag_id IS 'Required tag.';
COMMENT ON COLUMN ember_schema.attendance_template_time_rule_restriction.member_id IS
    'Specific member (always OR-connected, bypasses AND/OR mode).';

CREATE TABLE ember_schema.attendance_session_time_rule
(
    id                  SERIAL PRIMARY KEY,
    session_id          INT  NOT NULL REFERENCES ember_schema.attendance_session (id) ON DELETE CASCADE,
    position            INT  NOT NULL,
    label               TEXT,
    preparation_minutes INT  NOT NULL DEFAULT 0 CHECK (preparation_minutes BETWEEN 0 AND 1440),
    follow_up_minutes   INT  NOT NULL DEFAULT 0 CHECK (follow_up_minutes BETWEEN 0 AND 1440),
    restriction_mode    TEXT NOT NULL DEFAULT 'AND' CHECK (restriction_mode IN ('AND', 'OR')),
    named_kinds         INT  NOT NULL DEFAULT 0 CHECK (named_kinds >= 0)
);

CREATE INDEX idx_attendance_session_time_rule_session
    ON ember_schema.attendance_session_time_rule (session_id, position);

COMMENT ON TABLE ember_schema.attendance_session_time_rule IS
    'Preparation and follow-up time a sheet grants to the people it names, copied from its template when the sheet was made and corrected on the sheet since. What the report counts.';
COMMENT ON COLUMN ember_schema.attendance_session_time_rule.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.attendance_session_time_rule.session_id IS
    'The sheet the rule belongs to. Deleting the sheet deletes its rules.';
COMMENT ON COLUMN ember_schema.attendance_session_time_rule.position IS
    'Where the rule stands in the sheet''s list, starting at 0.';
COMMENT ON COLUMN ember_schema.attendance_session_time_rule.label IS
    'What the rule is for, such as a role name, shown beside the minutes. NULL where nobody named it.';
COMMENT ON COLUMN ember_schema.attendance_session_time_rule.preparation_minutes IS
    'Minutes of preparation a present person the rule names is credited with.';
COMMENT ON COLUMN ember_schema.attendance_session_time_rule.follow_up_minutes IS
    'Minutes of follow-up a present person the rule names is credited with.';
COMMENT ON COLUMN ember_schema.attendance_session_time_rule.restriction_mode IS
    'How the kinds named in attendance_session_time_rule_restriction combine: AND needs a match of every kind named, OR one of any. A rule naming nobody applies to nobody.';
COMMENT ON COLUMN ember_schema.attendance_session_time_rule.named_kinds IS
    'How many user types, groups and tags the rule named when it was written. A deleted group or tag removes its row from attendance_session_time_rule_restriction; a rule with fewer such rows than this credits nobody, so a deletion never widens it to people it did not name.';

CREATE TABLE ember_schema.attendance_session_time_rule_restriction
(
    id        SERIAL PRIMARY KEY,
    rule_id   INT NOT NULL REFERENCES ember_schema.attendance_session_time_rule (id) ON DELETE CASCADE,
    user_type TEXT,
    group_id  INT REFERENCES ember_schema.member_group (id) ON DELETE CASCADE,
    tag_id    INT REFERENCES ember_schema.user_tag (id) ON DELETE CASCADE,
    member_id INT REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    CHECK (num_nonnulls(user_type, group_id, tag_id, member_id) = 1)
);

CREATE INDEX idx_attendance_session_time_rule_restriction_rule
    ON ember_schema.attendance_session_time_rule_restriction (rule_id);
CREATE INDEX idx_attendance_session_time_rule_restriction_group
    ON ember_schema.attendance_session_time_rule_restriction (group_id) WHERE group_id IS NOT NULL;
CREATE INDEX idx_attendance_session_time_rule_restriction_tag
    ON ember_schema.attendance_session_time_rule_restriction (tag_id) WHERE tag_id IS NOT NULL;
CREATE INDEX idx_attendance_session_time_rule_restriction_member
    ON ember_schema.attendance_session_time_rule_restriction (member_id) WHERE member_id IS NOT NULL;

COMMENT ON TABLE ember_schema.attendance_session_time_rule_restriction IS
    'Whom a sheet time rule names. Shaped like event_restriction.';
COMMENT ON COLUMN ember_schema.attendance_session_time_rule_restriction.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.attendance_session_time_rule_restriction.rule_id IS 'References the rule.';
COMMENT ON COLUMN ember_schema.attendance_session_time_rule_restriction.user_type IS
    'Required user type. Exactly one of user_type/group_id/tag_id/member_id must be set.';
COMMENT ON COLUMN ember_schema.attendance_session_time_rule_restriction.group_id IS 'Required group membership.';
COMMENT ON COLUMN ember_schema.attendance_session_time_rule_restriction.tag_id IS 'Required tag.';
COMMENT ON COLUMN ember_schema.attendance_session_time_rule_restriction.member_id IS
    'Specific member (always OR-connected, bypasses AND/OR mode).';

ALTER TABLE ember_schema.attendance_entry
    ADD COLUMN skip_extra_time BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN ember_schema.attendance_entry.skip_extra_time IS
    'Whether this person did not do the preparation and follow-up their sheet''s time rules would credit them with this time. Set on the sheet; the report then counts their presence alone.';

INSERT INTO ember_schema.station_permission (name)
VALUES ('DOCUMENT_SIGN_OTHERS')
ON CONFLICT (name) DO NOTHING;

ALTER TABLE ember_schema.signing_evidence
    DROP CONSTRAINT IF EXISTS signing_evidence_capacity_check;

ALTER TABLE ember_schema.signing_evidence
    ADD CONSTRAINT signing_evidence_capacity_check
        CHECK (capacity IN ('ACCOUNT_HOLDER', 'GUARDIAN', 'MEMBER_THROUGH_ACCOUNT', 'WITNESSED'));

COMMENT ON CONSTRAINT signing_evidence_capacity_check ON ember_schema.signing_evidence IS
    'The capacities an act can be given in.';
COMMENT ON COLUMN ember_schema.signing_evidence.capacity IS
    'In what capacity the act was given: ACCOUNT_HOLDER for themselves, GUARDIAN on behalf of the member, MEMBER_THROUGH_ACCOUNT by the member through the account of somebody else, WITNESSED by a person who signed in front of a witness, whose account confirmed it.';
COMMENT ON COLUMN ember_schema.signing_evidence.account_member_id IS
    'The member at the station whose account confirmed the act, the witness for a WITNESSED act. NULL once that member was deleted.';
COMMENT ON COLUMN ember_schema.signing_evidence.account_holder_name IS
    'The official name of the account holder whose step-up confirmed the act, the witness for a WITNESSED act, as it was at signing.';
COMMENT ON COLUMN ember_schema.signing_evidence.member_id IS
    'The member a guardian signed for, who signed through the account, or who signed in front of the witness. NULL where the account holder signed for themselves, and once that member was deleted.';
COMMENT ON COLUMN ember_schema.signing_evidence.member_name IS
    'The official name of the member signed for, signing through the account, or signing in front of the witness, as it was at signing. NULL where the account holder signed for themselves.';
COMMENT ON COLUMN ember_schema.signing_evidence.guardian_position IS
    'Where the guardian stood in the member''s order of guardians at signing, counted from 0: the account holder when they acted as guardian or lent their account, the guardian who signed in front of a witness for a WITNESSED act. NULL where no guardian was involved.';

ALTER TABLE ember_schema.station
    ADD COLUMN IF NOT EXISTS witness_reach_mode TEXT NOT NULL DEFAULT 'AND' CHECK (witness_reach_mode IN ('AND', 'OR'));

COMMENT ON COLUMN ember_schema.station.witness_reach_mode IS
    'AND or OR, how the parts of witness_reach_restriction combine.';

CREATE TABLE IF NOT EXISTS ember_schema.witness_reach_restriction
(
    id         SERIAL PRIMARY KEY,
    station_id INT NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    user_type  TEXT,
    group_id   INT REFERENCES ember_schema.member_group (id) ON DELETE CASCADE,
    tag_id     INT REFERENCES ember_schema.user_tag (id) ON DELETE CASCADE,
    member_id  INT REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    CHECK (num_nonnulls(user_type, group_id, tag_id, member_id) = 1)
);

CREATE INDEX IF NOT EXISTS idx_witness_reach_restriction_station
    ON ember_schema.witness_reach_restriction (station_id);

COMMENT ON TABLE ember_schema.witness_reach_restriction IS
    'Whom the holders of DOCUMENT_SIGN_OTHERS at a station may collect signatures from in person, among its current members and trial users. Empty means all of them. Shaped like event_restriction.';
COMMENT ON COLUMN ember_schema.witness_reach_restriction.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.witness_reach_restriction.station_id IS 'The station whose witnesses the audience narrows.';
COMMENT ON COLUMN ember_schema.witness_reach_restriction.user_type IS
    'Required user type. Exactly one of user_type/group_id/tag_id/member_id must be set.';
COMMENT ON COLUMN ember_schema.witness_reach_restriction.group_id IS 'Required group membership.';
COMMENT ON COLUMN ember_schema.witness_reach_restriction.tag_id IS 'Required tag.';
COMMENT ON COLUMN ember_schema.witness_reach_restriction.member_id IS
    'Specific member (always OR-connected, bypasses AND/OR mode).';

DELETE
FROM ember_schema.document_template_restriction r
    USING ember_schema.document_template t
WHERE r.template_id = t.id
  AND NOT t.self_service;

DELETE
FROM ember_schema.document_template_station_use_restriction r
    USING ember_schema.document_template_station_use u
WHERE r.use_id = u.id
  AND NOT u.self_service;

COMMENT ON TABLE ember_schema.document_template_restriction IS
    'For whom of a station a template of the station is meant: the members its documents are generated for, by managers, runs, witnesses, appointments and self service. Empty means every member. A template of an association has none; each station sets its own audience in document_template_station_use_restriction. Shaped like event_restriction.';
COMMENT ON TABLE ember_schema.document_template_station_use_restriction IS
    'For whom of a station a template of its association is meant: the members its documents are generated for there, by managers, runs, witnesses, appointments and self service. Empty means every member. Shaped like event_restriction.';

ALTER TABLE ember_schema.procedure
    ADD COLUMN IF NOT EXISTS about_member_id  INT NULL REFERENCES ember_schema.station_member (id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS restriction_mode TEXT NOT NULL DEFAULT 'AND' CHECK (restriction_mode IN ('AND', 'OR'));

COMMENT ON COLUMN ember_schema.procedure.about_member_id IS
    'The member the procedure is about, whose documents its document steps generate. NULL for procedures from before it was asked for, and once that member was deleted.';
COMMENT ON COLUMN ember_schema.procedure.restriction_mode IS
    'AND or OR, how the parts of procedure_restriction combine.';

CREATE TABLE IF NOT EXISTS ember_schema.procedure_restriction
(
    id           SERIAL PRIMARY KEY,
    procedure_id INT NOT NULL REFERENCES ember_schema.procedure (id) ON DELETE CASCADE,
    user_type    TEXT,
    group_id     INT REFERENCES ember_schema.member_group (id) ON DELETE CASCADE,
    tag_id       INT REFERENCES ember_schema.user_tag (id) ON DELETE CASCADE,
    member_id    INT REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    CHECK (num_nonnulls(user_type, group_id, tag_id, member_id) = 1)
);

CREATE INDEX IF NOT EXISTS idx_procedure_restriction_procedure
    ON ember_schema.procedure_restriction (procedure_id);

COMMENT ON TABLE ember_schema.procedure_restriction IS
    'The managing party of a procedure: who sees its public steps, ticks the steps marked for them and is told about it. Empty means nobody beyond those who edit procedures. Shaped like event_restriction.';
COMMENT ON COLUMN ember_schema.procedure_restriction.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.procedure_restriction.procedure_id IS 'The procedure the party runs.';
COMMENT ON COLUMN ember_schema.procedure_restriction.user_type IS
    'Required user type. Exactly one of user_type/group_id/tag_id/member_id must be set.';
COMMENT ON COLUMN ember_schema.procedure_restriction.group_id IS 'Required group membership.';
COMMENT ON COLUMN ember_schema.procedure_restriction.tag_id IS 'Required tag.';
COMMENT ON COLUMN ember_schema.procedure_restriction.member_id IS
    'Specific member (always OR-connected, bypasses AND/OR mode).';

INSERT INTO ember_schema.procedure_restriction (procedure_id, member_id)
SELECT procedure_id, member_id
FROM ember_schema.procedure_assignee;

DROP TABLE IF EXISTS ember_schema.procedure_assignee;

ALTER TABLE ember_schema.procedure_template
    ADD COLUMN IF NOT EXISTS restriction_mode TEXT NOT NULL DEFAULT 'AND' CHECK (restriction_mode IN ('AND', 'OR'));

COMMENT ON COLUMN ember_schema.procedure_template.restriction_mode IS
    'AND or OR, how the parts of procedure_template_restriction combine.';

CREATE TABLE IF NOT EXISTS ember_schema.procedure_template_restriction
(
    id          SERIAL PRIMARY KEY,
    template_id INT NOT NULL REFERENCES ember_schema.procedure_template (id) ON DELETE CASCADE,
    user_type   TEXT,
    group_id    INT REFERENCES ember_schema.member_group (id) ON DELETE CASCADE,
    tag_id      INT REFERENCES ember_schema.user_tag (id) ON DELETE CASCADE,
    member_id   INT REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    CHECK (num_nonnulls(user_type, group_id, tag_id, member_id) = 1)
);

CREATE INDEX IF NOT EXISTS idx_procedure_template_restriction_template
    ON ember_schema.procedure_template_restriction (template_id);

COMMENT ON TABLE ember_schema.procedure_template_restriction IS
    'The managing party a procedure started from the template begins with, copied into the procedure and changed there. Empty means none. Shaped like event_restriction.';
COMMENT ON COLUMN ember_schema.procedure_template_restriction.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.procedure_template_restriction.template_id IS 'The procedure template.';
COMMENT ON COLUMN ember_schema.procedure_template_restriction.user_type IS
    'Required user type. Exactly one of user_type/group_id/tag_id/member_id must be set.';
COMMENT ON COLUMN ember_schema.procedure_template_restriction.group_id IS 'Required group membership.';
COMMENT ON COLUMN ember_schema.procedure_template_restriction.tag_id IS 'Required tag.';
COMMENT ON COLUMN ember_schema.procedure_template_restriction.member_id IS
    'Specific member (always OR-connected, bypasses AND/OR mode).';

ALTER TABLE ember_schema.procedure_template_item
    ADD COLUMN IF NOT EXISTS kind                 TEXT NOT NULL DEFAULT 'CHECK' CHECK (kind IN ('CHECK', 'DOCUMENT')),
    ADD COLUMN IF NOT EXISTS document_template_id INT NULL REFERENCES ember_schema.document_template (id) ON DELETE SET NULL;

COMMENT ON COLUMN ember_schema.procedure_template_item.kind IS
    'CHECK for a step ticked by hand, DOCUMENT for a document to sign that ticks itself once settled.';
COMMENT ON COLUMN ember_schema.procedure_template_item.document_template_id IS
    'The document template a DOCUMENT step generates. NULL for a CHECK step, and once the template was deleted.';

ALTER TABLE ember_schema.procedure_item
    ADD COLUMN IF NOT EXISTS kind                 TEXT NOT NULL DEFAULT 'CHECK' CHECK (kind IN ('CHECK', 'DOCUMENT')),
    ADD COLUMN IF NOT EXISTS document_template_id INT NULL REFERENCES ember_schema.document_template (id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS generation_id        INT NULL REFERENCES ember_schema.document_generation (id) ON DELETE SET NULL;

COMMENT ON COLUMN ember_schema.procedure_item.kind IS
    'CHECK for a step ticked by hand, DOCUMENT for a document to sign that ticks itself once settled.';
COMMENT ON COLUMN ember_schema.procedure_item.document_template_id IS
    'The document template a DOCUMENT step generates. NULL for a CHECK step, and once the template was deleted.';
COMMENT ON COLUMN ember_schema.procedure_item.generation_id IS
    'The document a DOCUMENT step generated, whose signature request settles it. NULL until the step unlocks, and once that entry was deleted.';
