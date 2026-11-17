-- A group or a tag can take its members from a rule instead of a hand-kept list. A rule is a tree of
-- blocks (all of, any of, none of) and checks on member data. Who matches is written into the same
-- member_group_entry and user_tag_entry rows a hand-kept group or tag uses.

CREATE TABLE ember_schema.member_rule_node
(
    id               SERIAL PRIMARY KEY,
    station_id       INTEGER NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    group_id         INTEGER NULL REFERENCES ember_schema.member_group (id) ON DELETE CASCADE,
    tag_id           INTEGER NULL REFERENCES ember_schema.user_tag (id) ON DELETE CASCADE,
    parent_id        INTEGER NULL REFERENCES ember_schema.member_rule_node (id) ON DELETE CASCADE,
    position         INTEGER NOT NULL DEFAULT 0,
    kind             TEXT    NOT NULL CHECK (kind IN ('ALL', 'ANY', 'NONE', 'CHECK')),
    subject          TEXT    NULL CHECK (subject IN ('GROUP', 'TAG', 'USER_TYPE', 'MEMBER_SINCE', 'AGE', 'FIELD',
                                                     'CLUSTER_FIELD')),
    operator         TEXT    NULL CHECK (operator IN ('IN', 'NOT_IN', 'ONE_OF', 'NONE_OF', 'AT_LEAST', 'LESS_THAN',
                                                      'BETWEEN', 'IS_YES', 'IS_NO', 'IS_NOT_YES', 'EMPTY',
                                                      'NOT_EMPTY', 'EQUALS', 'CONTAINS', 'EQ', 'LT', 'LE', 'GT',
                                                      'GE', 'BEFORE', 'ON_OR_AFTER', 'WITHIN_NEXT', 'WITHIN_LAST',
                                                      'EXPIRES_WITHIN', 'EXPIRED', 'STATE_IN')),
    field_id         INTEGER NULL REFERENCES ember_schema.profile_field (id),
    cluster_field_id INTEGER NULL REFERENCES ember_schema.cluster_profile_field (id) ON DELETE SET NULL,
    ref_group_id     INTEGER NULL REFERENCES ember_schema.member_group (id),
    ref_tag_id       INTEGER NULL REFERENCES ember_schema.user_tag (id),
    value            JSONB   NULL,
    reference_date   DATE    NULL,
    CONSTRAINT member_rule_node_root_names_its_owner
        CHECK ((parent_id IS NULL AND num_nonnulls(group_id, tag_id) = 1)
            OR (parent_id IS NOT NULL AND group_id IS NULL AND tag_id IS NULL)),
    CONSTRAINT member_rule_node_root_is_a_block
        CHECK (parent_id IS NOT NULL OR kind <> 'CHECK'),
    CONSTRAINT member_rule_node_check_says_what
        CHECK ((kind = 'CHECK') = (subject IS NOT NULL AND operator IS NOT NULL))
);

CREATE UNIQUE INDEX idx_member_rule_node_group ON ember_schema.member_rule_node (group_id) WHERE group_id IS NOT NULL;
CREATE UNIQUE INDEX idx_member_rule_node_tag ON ember_schema.member_rule_node (tag_id) WHERE tag_id IS NOT NULL;
CREATE INDEX idx_member_rule_node_station ON ember_schema.member_rule_node (station_id);
CREATE INDEX idx_member_rule_node_parent ON ember_schema.member_rule_node (parent_id) WHERE parent_id IS NOT NULL;
CREATE INDEX idx_member_rule_node_field ON ember_schema.member_rule_node (field_id) WHERE field_id IS NOT NULL;
CREATE INDEX idx_member_rule_node_cluster_field
    ON ember_schema.member_rule_node (cluster_field_id) WHERE cluster_field_id IS NOT NULL;
CREATE INDEX idx_member_rule_node_ref_group ON ember_schema.member_rule_node (ref_group_id) WHERE ref_group_id IS NOT NULL;
CREATE INDEX idx_member_rule_node_ref_tag ON ember_schema.member_rule_node (ref_tag_id) WHERE ref_tag_id IS NOT NULL;

COMMENT ON TABLE ember_schema.member_rule_node
    IS 'One node of the rule a dynamic group or tag takes its members from: a block joining its children, or a check on member data. The root names the group or tag; a group or tag with a root is kept by its rule and refuses hand edits.';
COMMENT ON COLUMN ember_schema.member_rule_node.id
    IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.member_rule_node.station_id
    IS 'The station the rule belongs to.';
COMMENT ON COLUMN ember_schema.member_rule_node.group_id
    IS 'On the root node of a group''s rule: the group the rule fills. NULL on every other node. Deleted with the group.';
COMMENT ON COLUMN ember_schema.member_rule_node.tag_id
    IS 'On the root node of a tag''s rule: the tag the rule fills. NULL on every other node. Deleted with the tag.';
COMMENT ON COLUMN ember_schema.member_rule_node.parent_id
    IS 'The block this node sits in. NULL on the root.';
COMMENT ON COLUMN ember_schema.member_rule_node.position
    IS 'The order of the node among the children of its block.';
COMMENT ON COLUMN ember_schema.member_rule_node.kind
    IS 'ALL (every child holds), ANY (at least one child holds), NONE (no child holds) or CHECK (a single check).';
COMMENT ON COLUMN ember_schema.member_rule_node.subject
    IS 'On a check: what it looks at (GROUP, TAG, USER_TYPE, MEMBER_SINCE, AGE, FIELD, CLUSTER_FIELD). NULL on a block.';
COMMENT ON COLUMN ember_schema.member_rule_node.operator
    IS 'On a check: how it compares, for example IN, ONE_OF, AT_LEAST, IS_NO, EMPTY or EXPIRES_WITHIN. NULL on a block.';
COMMENT ON COLUMN ember_schema.member_rule_node.field_id
    IS 'The station profile field a field check or an age check reads. A field in use by a rule cannot be deleted.';
COMMENT ON COLUMN ember_schema.member_rule_node.cluster_field_id
    IS 'The association profile field a field check or an age check reads. Emptied when the association deletes the field; such a check then matches nobody.';
COMMENT ON COLUMN ember_schema.member_rule_node.ref_group_id
    IS 'The group a group check asks about. A group in use by a rule cannot be deleted.';
COMMENT ON COLUMN ember_schema.member_rule_node.ref_tag_id
    IS 'The tag a tag check asks about. A tag in use by a rule cannot be deleted.';
COMMENT ON COLUMN ember_schema.member_rule_node.value
    IS 'What a check compares with: answers, user types, expiry states, a number or range, a text, a date or a duration. NULL where the operator needs nothing.';
COMMENT ON COLUMN ember_schema.member_rule_node.reference_date
    IS 'The day a date-relative check counts from, such as an age on a fixed date. NULL means the current day.';
