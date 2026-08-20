-- The cluster: a body that owns several stations.
--
-- A cluster is not a station, but it owns one. Every cluster is created with a home station: a station row
-- nobody joins, nobody sees in a switcher and no member ever opens. The home station is where the cluster's
-- content, its inventory pool and its federation identity live, which is what lets cluster content travel to
-- member stations through the federation machinery that already exists rather than through a second,
-- cluster-shaped copy of it.
--
-- The permission tables are a mirror of the station's, deliberately: a cluster has its own members, its own
-- user types, its own groups and its own grants, and none of them are station rows wearing a hat.

ALTER TABLE ember_schema.station
    ADD COLUMN IF NOT EXISTS station_kind TEXT NOT NULL DEFAULT 'REGULAR';

COMMENT ON COLUMN ember_schema.station.station_kind
    IS 'REGULAR for a station somebody joins, CLUSTER_HOME for the shell a cluster owns and nobody is a member of.';

CREATE TABLE IF NOT EXISTS ember_schema.cluster
(
    id                 SERIAL PRIMARY KEY,
    uid                UUID                     NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    name               TEXT                     NOT NULL,
    description        TEXT,
    home_station_id    INTEGER                  NOT NULL REFERENCES ember_schema.station (id) ON DELETE RESTRICT,
    auto_federate      BOOLEAN                  NOT NULL DEFAULT TRUE,
    theme_locked       BOOLEAN                  NOT NULL DEFAULT FALSE,
    colors_locked      BOOLEAN                  NOT NULL DEFAULT FALSE,
    feel_locked        BOOLEAN                  NOT NULL DEFAULT FALSE,
    logo_locked        BOOLEAN                  NOT NULL DEFAULT FALSE,
    storage_pool_bytes BIGINT,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    UNIQUE (home_station_id)
);

COMMENT ON TABLE ember_schema.cluster
    IS 'A body that owns several stations: a district association, an umbrella organisation, a municipality.';
COMMENT ON COLUMN ember_schema.cluster.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.cluster.uid IS 'Stable identity used wherever a cluster is named across instances.';
COMMENT ON COLUMN ember_schema.cluster.name IS 'What the cluster is called. The home station is kept in step with it.';
COMMENT ON COLUMN ember_schema.cluster.description IS 'A sentence about the cluster, shown where it is presented.';
COMMENT ON COLUMN ember_schema.cluster.home_station_id IS 'The station shell the cluster owns, where its content and its inventory pool live. Restricted on delete because the cluster cannot exist without it.';
COMMENT ON COLUMN ember_schema.cluster.auto_federate IS 'Whether member stations are paired with the cluster and with each other as they join.';
COMMENT ON COLUMN ember_schema.cluster.theme_locked IS 'Whether member stations may change their theme.';
COMMENT ON COLUMN ember_schema.cluster.colors_locked IS 'Whether member stations may change their colours.';
COMMENT ON COLUMN ember_schema.cluster.feel_locked IS 'Whether member stations may change the rest of their look and feel.';
COMMENT ON COLUMN ember_schema.cluster.logo_locked IS 'Whether member stations may change their logo.';
COMMENT ON COLUMN ember_schema.cluster.storage_pool_bytes IS 'How much storage the cluster has to hand out to its stations, or null for no pool of its own.';
COMMENT ON COLUMN ember_schema.cluster.created_at IS 'When the cluster was created.';

-- The station-side anchor. A station released from its cluster goes back to null.
ALTER TABLE ember_schema.station
    ADD COLUMN IF NOT EXISTS cluster_id INTEGER REFERENCES ember_schema.cluster (id) ON DELETE SET NULL;

COMMENT ON COLUMN ember_schema.station.cluster_id
    IS 'The cluster this station belongs to, or null when it answers to nobody.';

CREATE INDEX IF NOT EXISTS idx_station_cluster ON ember_schema.station (cluster_id) WHERE cluster_id IS NOT NULL;

-- Cluster members are account-level, with no former semantics: revoking a membership deletes the row.
CREATE TABLE IF NOT EXISTS ember_schema.cluster_member
(
    id         SERIAL PRIMARY KEY,
    cluster_id INTEGER NOT NULL REFERENCES ember_schema.cluster (id) ON DELETE CASCADE,
    account_id INTEGER NOT NULL REFERENCES ember_schema.account (id) ON DELETE CASCADE,
    user_type  TEXT    NOT NULL DEFAULT 'CLUSTER_USER',
    UNIQUE (cluster_id, account_id)
);

COMMENT ON TABLE ember_schema.cluster_member
    IS 'An account acting on a cluster''s behalf. There is no unique constraint on the account alone: one account may belong to several clusters.';
COMMENT ON COLUMN ember_schema.cluster_member.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.cluster_member.cluster_id IS 'The cluster they belong to.';
COMMENT ON COLUMN ember_schema.cluster_member.account_id IS 'The account behind them.';
COMMENT ON COLUMN ember_schema.cluster_member.user_type IS 'Their user type, which carries a set of permissions by default.';

CREATE INDEX IF NOT EXISTS idx_cluster_member_account ON ember_schema.cluster_member (account_id);

CREATE TABLE IF NOT EXISTS ember_schema.cluster_permission
(
    id   SERIAL PRIMARY KEY,
    name TEXT NOT NULL UNIQUE
);

COMMENT ON TABLE ember_schema.cluster_permission IS 'The permissions a cluster member can hold.';
COMMENT ON COLUMN ember_schema.cluster_permission.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.cluster_permission.name IS 'The permission''s name, matching the enum in the code.';

CREATE TABLE IF NOT EXISTS ember_schema.cluster_member_permission
(
    member_id     INTEGER NOT NULL REFERENCES ember_schema.cluster_member (id) ON DELETE CASCADE,
    permission_id INTEGER NOT NULL REFERENCES ember_schema.cluster_permission (id) ON DELETE CASCADE,
    PRIMARY KEY (member_id, permission_id)
);

COMMENT ON TABLE ember_schema.cluster_member_permission IS 'Permissions granted to one cluster member directly.';
COMMENT ON COLUMN ember_schema.cluster_member_permission.member_id IS 'The member holding it.';
COMMENT ON COLUMN ember_schema.cluster_member_permission.permission_id IS 'The permission held.';

CREATE TABLE IF NOT EXISTS ember_schema.cluster_user_type_permission
(
    cluster_id    INTEGER NOT NULL REFERENCES ember_schema.cluster (id) ON DELETE CASCADE,
    user_type     TEXT    NOT NULL,
    permission_id INTEGER NOT NULL REFERENCES ember_schema.cluster_permission (id) ON DELETE CASCADE,
    PRIMARY KEY (cluster_id, user_type, permission_id)
);

COMMENT ON TABLE ember_schema.cluster_user_type_permission IS 'Permissions a cluster grants to everybody of a given user type.';
COMMENT ON COLUMN ember_schema.cluster_user_type_permission.cluster_id IS 'The cluster whose rule this is.';
COMMENT ON COLUMN ember_schema.cluster_user_type_permission.user_type IS 'The user type it applies to.';
COMMENT ON COLUMN ember_schema.cluster_user_type_permission.permission_id IS 'The permission granted.';

CREATE TABLE IF NOT EXISTS ember_schema.cluster_member_group
(
    id         SERIAL PRIMARY KEY,
    cluster_id INTEGER NOT NULL REFERENCES ember_schema.cluster (id) ON DELETE CASCADE,
    name       TEXT    NOT NULL,
    UNIQUE (cluster_id, name)
);

COMMENT ON TABLE ember_schema.cluster_member_group IS 'A named group of cluster members, which permissions can be hung off.';
COMMENT ON COLUMN ember_schema.cluster_member_group.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.cluster_member_group.cluster_id IS 'The cluster the group belongs to.';
COMMENT ON COLUMN ember_schema.cluster_member_group.name IS 'What the group is called, unique within the cluster.';

CREATE TABLE IF NOT EXISTS ember_schema.cluster_member_group_membership
(
    group_id  INTEGER NOT NULL REFERENCES ember_schema.cluster_member_group (id) ON DELETE CASCADE,
    member_id INTEGER NOT NULL REFERENCES ember_schema.cluster_member (id) ON DELETE CASCADE,
    PRIMARY KEY (group_id, member_id)
);

COMMENT ON TABLE ember_schema.cluster_member_group_membership IS 'Which cluster members are in which group.';
COMMENT ON COLUMN ember_schema.cluster_member_group_membership.group_id IS 'The group.';
COMMENT ON COLUMN ember_schema.cluster_member_group_membership.member_id IS 'The member in it.';

CREATE TABLE IF NOT EXISTS ember_schema.cluster_member_group_permission
(
    group_id      INTEGER NOT NULL REFERENCES ember_schema.cluster_member_group (id) ON DELETE CASCADE,
    permission_id INTEGER NOT NULL REFERENCES ember_schema.cluster_permission (id) ON DELETE CASCADE,
    PRIMARY KEY (group_id, permission_id)
);

COMMENT ON TABLE ember_schema.cluster_member_group_permission IS 'Permissions granted to everybody in a group.';
COMMENT ON COLUMN ember_schema.cluster_member_group_permission.group_id IS 'The group holding it.';
COMMENT ON COLUMN ember_schema.cluster_member_group_permission.permission_id IS 'The permission held.';

INSERT INTO ember_schema.cluster_permission (name)
VALUES ('USER'), ('LOGIN'),
       ('CLUSTER_GENERAL'), ('CLUSTER_LOOK_AND_FEEL'), ('CLUSTER_FEDERATION'),
       ('CLUSTER_MODULES'), ('CLUSTER_STORAGE'), ('CLUSTER_STATIONS'),
       ('CLUSTER_MEMBER_READ'), ('CLUSTER_MEMBER_EDIT'), ('CLUSTER_MEMBER_FIELDS'),
       ('CLUSTER_MEMBER_EXPORT'), ('CLUSTER_MEMBER_MANAGER'),
       ('CLUSTER_INVENTORY_READ'), ('CLUSTER_INVENTORY_EDIT'), ('CLUSTER_INVENTORY_TRANSFER'),
       ('CLUSTER_INVENTORY_EXCHANGE'), ('CLUSTER_INVENTORY_MANAGER'),
       ('CLUSTER_FIELD_EDIT'), ('CLUSTER_FIELD_MANAGER'),
       ('CLUSTER_KNOWLEDGE_EDIT'), ('CLUSTER_KNOWLEDGE_MANAGER'),
       ('CLUSTER_NEWS_EDIT'), ('CLUSTER_NEWS_MANAGER'),
       ('CLUSTER_EVENT_EDIT'), ('CLUSTER_EVENT_MANAGER'),
       ('CLUSTER_MANAGER'), ('CLUSTER_ADMINISTRATOR')
ON CONFLICT (name) DO NOTHING;

-- Two columns have been waiting for this table since the inventory work named them. An item owned by the
-- body above a station, and a flow that body sets the terms of, can now say which body they mean.
ALTER TABLE ember_schema.inventory_item
    DROP CONSTRAINT IF EXISTS fk_inventory_item_owner_cluster;

ALTER TABLE ember_schema.inventory_item
    ADD CONSTRAINT fk_inventory_item_owner_cluster
        FOREIGN KEY (owner_cluster_id) REFERENCES ember_schema.cluster (id) ON DELETE SET NULL;

ALTER TABLE ember_schema.movement_flow
    DROP CONSTRAINT IF EXISTS fk_movement_flow_cluster;

ALTER TABLE ember_schema.movement_flow
    ADD CONSTRAINT fk_movement_flow_cluster
        FOREIGN KEY (cluster_id) REFERENCES ember_schema.cluster (id) ON DELETE CASCADE;
