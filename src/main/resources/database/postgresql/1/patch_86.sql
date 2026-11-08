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
