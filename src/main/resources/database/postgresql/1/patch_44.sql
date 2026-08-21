-- Questions a cluster asks of the people at its stations.
--
-- A station already decides what it wants to know about its members. A cluster has questions of its own, and
-- they are the same kind of thing: a name, a type, a position and a place in the profile. So the shape here
-- mirrors profile_field almost exactly, and the value hangs off the same station_member row, because the
-- person being asked is a member of a station and not of the cluster.
--
-- Two columns the station's own fields do not have. station_readonly says whether the people at the station
-- may fill the answer in or only read it, which a station field never has to ask because a station field
-- belongs to the people looking at it. And the whole row is scoped to the cluster rather than the station,
-- so one question is asked once and answered at every station under it.

CREATE TABLE IF NOT EXISTS ember_schema.cluster_profile_field
(
    id               SERIAL PRIMARY KEY,
    cluster_id       INTEGER NOT NULL REFERENCES ember_schema.cluster (id) ON DELETE CASCADE,
    name             TEXT    NOT NULL,
    field_type       TEXT    NOT NULL DEFAULT 'TEXT',
    config           JSONB            DEFAULT '{}',
    position         INTEGER NOT NULL DEFAULT 0,
    scope            TEXT    NOT NULL DEFAULT 'MEMBER',
    station_readonly BOOLEAN NOT NULL DEFAULT TRUE,
    keep_on_archive  BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (cluster_id, scope, name)
);

COMMENT ON TABLE ember_schema.cluster_profile_field
    IS 'A question a cluster asks of the members at its stations, shaped like a station''s own profile field.';
COMMENT ON COLUMN ember_schema.cluster_profile_field.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.cluster_profile_field.cluster_id IS 'The cluster asking.';
COMMENT ON COLUMN ember_schema.cluster_profile_field.name IS 'The label, unique within its cluster and scope.';
COMMENT ON COLUMN ember_schema.cluster_profile_field.field_type
    IS 'What kind of answer it takes, from the same set a station field uses.';
COMMENT ON COLUMN ember_schema.cluster_profile_field.config
    IS 'The same settings a station field carries: required, read-only, notify on change and the rest.';
COMMENT ON COLUMN ember_schema.cluster_profile_field.position IS 'Where it sits among the cluster''s own fields.';
COMMENT ON COLUMN ember_schema.cluster_profile_field.scope
    IS 'Which kind of member it applies to. Group scope is refused: a group is a station''s own and a cluster cannot see it.';
COMMENT ON COLUMN ember_schema.cluster_profile_field.station_readonly
    IS 'TRUE when the people at the station may read the answer but not write it.';
COMMENT ON COLUMN ember_schema.cluster_profile_field.keep_on_archive
    IS 'TRUE when the answer survives the member being marked as having left.';

CREATE TABLE IF NOT EXISTS ember_schema.cluster_profile_field_value
(
    member_id INTEGER NOT NULL REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    field_id  INTEGER NOT NULL REFERENCES ember_schema.cluster_profile_field (id) ON DELETE CASCADE,
    value     JSONB DEFAULT '{}',
    PRIMARY KEY (member_id, field_id)
);

COMMENT ON TABLE ember_schema.cluster_profile_field_value
    IS 'What one member answered to one cluster question. Keyed on the station member, because that is who was asked.';
COMMENT ON COLUMN ember_schema.cluster_profile_field_value.member_id IS 'The member who answered.';
COMMENT ON COLUMN ember_schema.cluster_profile_field_value.field_id IS 'The question.';
COMMENT ON COLUMN ember_schema.cluster_profile_field_value.value IS 'The answer, in the same shape a station field''s is.';

-- One history for both kinds of field, so a member's profile reads as one story rather than two.

ALTER TABLE ember_schema.profile_field_change
    ALTER COLUMN field_id DROP NOT NULL;

ALTER TABLE ember_schema.profile_field_change
    ADD COLUMN IF NOT EXISTS cluster_field_id INTEGER
        REFERENCES ember_schema.cluster_profile_field (id) ON DELETE CASCADE;

COMMENT ON COLUMN ember_schema.profile_field_change.field_id
    IS 'The station''s own field that changed, or null when a cluster''s field changed instead.';
COMMENT ON COLUMN ember_schema.profile_field_change.cluster_field_id
    IS 'The cluster field that changed, or null when the station''s own field changed instead.';

ALTER TABLE ember_schema.profile_field_change
    DROP CONSTRAINT IF EXISTS chk_profile_field_change_target;
ALTER TABLE ember_schema.profile_field_change
    ADD CONSTRAINT chk_profile_field_change_target CHECK (num_nonnulls(field_id, cluster_field_id) = 1);

CREATE INDEX IF NOT EXISTS idx_cluster_profile_field_cluster
    ON ember_schema.cluster_profile_field (cluster_id, scope, position);
