-- Item ownership and custody, and the movements between parties that change them.
--
-- Three changes that only make sense together, so they arrive together:
--
--   1. Who owns an item, replacing a column that claimed members owned tracked gear.
--   2. Who has it right now, which nothing recorded before.
--   3. Movements between parties as configurable flows, replacing an exchange chain of five
--      hardcoded statuses that the station ticked on everybody's behalf.
--
-- The order matters and is the order below: custody is filled in from what ownership says, and the
-- movement migration reads both.

-- Who owns an inventory item.
--
-- item_source said INTERNAL or EXTERNAL and was documented as "owned by the organization" against
-- "owned by the member". The second half was never true: members do not own tracked gear. What
-- EXTERNAL has always meant in practice is "owned by the body above this station", the municipality,
-- the district association or the umbrella organisation. owner_kind says that outright, and
-- owner_cluster_id names the body when it runs on this instance and is null when it does not.
--
-- This is a reinterpretation, not a data move. Every INTERNAL row becomes STATION, every EXTERNAL
-- row becomes CLUSTER with no cluster named, which is exactly what those rows have always meant.
--
-- owner_cluster_id carries no foreign key yet because the cluster table does not exist. The
-- reference is added by the patch that creates it.

ALTER TABLE ember_schema.inventory_item
    ADD COLUMN IF NOT EXISTS owner_kind       TEXT NOT NULL DEFAULT 'STATION',
    ADD COLUMN IF NOT EXISTS owner_cluster_id INTEGER;

UPDATE ember_schema.inventory_item
SET owner_kind = CASE WHEN item_source = 'EXTERNAL' THEN 'CLUSTER' ELSE 'STATION' END;

ALTER TABLE ember_schema.inventory_item
    DROP CONSTRAINT IF EXISTS chk_inventory_item_owner;

ALTER TABLE ember_schema.inventory_item
    ADD CONSTRAINT chk_inventory_item_owner
        CHECK (owner_kind = 'CLUSTER' OR owner_cluster_id IS NULL);

COMMENT ON COLUMN ember_schema.inventory_item.owner_kind
    IS 'Who owns the item: STATION for the station running its inventory, CLUSTER for the one body above that station.';
COMMENT ON COLUMN ember_schema.inventory_item.owner_cluster_id
    IS 'The owning body when it runs on this instance, null when it owns the item without using Ember. Only ever set for CLUSTER.';

ALTER TABLE ember_schema.inventory_item
    DROP COLUMN IF EXISTS item_source;

-- The second ownership flag. It lived in the metadata object, was never set by a user, was never
-- read by the frontend, and contradicted item_source wherever both had an opinion.
UPDATE ember_schema.inventory_item
SET metadata = metadata - 'owned'
WHERE jsonb_exists(metadata, 'owned');


-- Where an item is right now.
--
-- Ownership says whose it is. Custody says who has it, and until now nothing did: an item was
-- either with a member or it was not, so gear posted back to the body above the station looked
-- exactly like gear lying in the store.
--
-- Custody is stored rather than derived, because deriving it from three nullable pointers is what
-- the four overlapping signals of the previous patch grew out of. Each value carries exactly one
-- set of pointers and a CHECK per value says which.
--
-- custody_movement_id carries no foreign key yet because the movement table does not exist. The
-- reference is added by the patch that creates it.

ALTER TABLE ember_schema.inventory_item
    ADD COLUMN IF NOT EXISTS custody             TEXT NOT NULL DEFAULT 'WITH_OWNER',
    ADD COLUMN IF NOT EXISTS custody_station_id  INTEGER REFERENCES ember_schema.station (id) ON DELETE SET NULL,
    ADD COLUMN IF NOT EXISTS custody_movement_id INTEGER;

-- Read off what the row already says. A lost timestamp gives LOST, a member holding it gives
-- WITH_MEMBER, and gear the station does not own that is neither is being held by that station,
-- which is AT_STATION. Only a station's own gear in its own store is WITH_OWNER, because there the
-- station is the owner.
--
-- A lost item keeps the member it was with. Gear that has gone missing stays on that member's
-- record until it is replaced, which is what makes it visible that they are short of it.
UPDATE ember_schema.inventory_item ii
SET custody            = CASE
                             WHEN ii.lost_at IS NOT NULL THEN 'LOST'
                             WHEN ii.assigned_to IS NOT NULL THEN 'WITH_MEMBER'
                             WHEN ii.owner_kind = 'CLUSTER' THEN 'AT_STATION'
                             ELSE 'WITH_OWNER'
                         END,
    custody_station_id = CASE
                             WHEN ii.lost_at IS NOT NULL THEN i.station_id
                             WHEN ii.assigned_to IS NOT NULL THEN i.station_id
                             WHEN ii.owner_kind = 'CLUSTER' THEN i.station_id
                             ELSE NULL
                         END
FROM ember_schema.inventory i
WHERE i.id = ii.inventory_id;

ALTER TABLE ember_schema.inventory_item
    DROP CONSTRAINT IF EXISTS chk_inventory_item_custody;

-- Each custody value forbids the pointers that do not belong to it. The check says which pointers
-- must be empty rather than which must be filled, and that asymmetry is deliberate: the station and
-- the member both clear themselves when the row they point at is deleted, so "AT_STATION names a
-- station" is an invariant no row check can hold on to. Deleting a station leaves gear it held
-- saying it is at a station that is gone, which is a row waiting to be re-homed rather than a lie.
-- Filling the pointers is the custody service's job; forbidding the ones that would contradict the
-- custody is the database's, and those combinations no deletion can create.
ALTER TABLE ember_schema.inventory_item
    ADD CONSTRAINT chk_inventory_item_custody CHECK (
        CASE custody
            WHEN 'WITH_OWNER' THEN custody_station_id IS NULL AND custody_movement_id IS NULL
                AND assigned_to IS NULL AND lost_at IS NULL
            WHEN 'AT_STATION' THEN custody_movement_id IS NULL
                AND assigned_to IS NULL AND lost_at IS NULL
            WHEN 'WITH_MEMBER' THEN custody_movement_id IS NULL
                AND lost_at IS NULL
            WHEN 'WITH_PARTNER' THEN custody_movement_id IS NULL
                AND assigned_to IS NULL AND lost_at IS NULL
            WHEN 'IN_TRANSIT' THEN assigned_to IS NULL AND lost_at IS NULL
            WHEN 'LOST' THEN custody_movement_id IS NULL AND lost_at IS NOT NULL
            ELSE FALSE
        END
    );

CREATE INDEX IF NOT EXISTS idx_inventory_item_custody_station
    ON ember_schema.inventory_item (custody_station_id)
    WHERE custody_station_id IS NOT NULL;

COMMENT ON COLUMN ember_schema.inventory_item.custody
    IS 'Who has the item right now: WITH_OWNER in the owner''s own store, AT_STATION held by a station that does not own it, WITH_MEMBER held by a member, WITH_PARTNER lent to a federation partner, IN_TRANSIT between two parties, LOST missing and still on the record of whoever had it.';
COMMENT ON COLUMN ember_schema.inventory_item.custody_station_id
    IS 'The station the custody runs through: the holder for AT_STATION, the station a member holds it through for WITH_MEMBER, the lender for WITH_PARTNER, and the holding station for LOST. Null for WITH_OWNER.';
COMMENT ON COLUMN ember_schema.inventory_item.custody_movement_id
    IS 'The movement holding the item while it is IN_TRANSIT. Null for every other custody.';


-- Movements between parties, and the flows they walk.
--
-- An exchange was a hardcoded chain of five statuses, two of them documented as belonging to the
-- external case only, and every one of them ticked by the station. A station cannot know when the
-- body above it received a parcel or posted a replacement, so the record asserted things nobody
-- observed.
--
-- A flow is a list of steps, and a step says three things: who acknowledges it, which item it is
-- about, and what custody that item is in once it is acknowledged. There is no list of step kinds,
-- because "collect", "send", "receive" and "hand over" are all just a party and a resulting
-- custody. The label is free text and can be renamed without changing behaviour.

CREATE TABLE IF NOT EXISTS ember_schema.movement_flow
(
    id         SERIAL PRIMARY KEY,
    station_id INTEGER REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    cluster_id INTEGER,
    name       TEXT    NOT NULL,
    purpose    TEXT    NOT NULL,
    archived   BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT chk_movement_flow_owner CHECK (num_nonnulls(station_id, cluster_id) = 1)
);

COMMENT ON TABLE ember_schema.movement_flow
    IS 'A named chain of steps a movement between two parties walks. Owned by the station or by the body above it.';
COMMENT ON COLUMN ember_schema.movement_flow.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.movement_flow.station_id IS 'The station whose flow this is, or null when the body above owns it.';
COMMENT ON COLUMN ember_schema.movement_flow.cluster_id IS 'The body above whose flow this is, once such a body runs on this instance. No reference yet, because that table does not exist.';
COMMENT ON COLUMN ember_schema.movement_flow.name IS 'What the flow is called where it is configured.';
COMMENT ON COLUMN ember_schema.movement_flow.purpose IS 'What kind of movement this flow is for: ISSUE, RETURN or EXCHANGE.';
COMMENT ON COLUMN ember_schema.movement_flow.archived IS 'Whether the flow is retired. Retired flows are not offered to new movements and still render in the history of the ones that walked them.';

CREATE TABLE IF NOT EXISTS ember_schema.movement_flow_step
(
    id            SERIAL PRIMARY KEY,
    flow_id       INTEGER NOT NULL REFERENCES ember_schema.movement_flow (id) ON DELETE CASCADE,
    position      INTEGER NOT NULL DEFAULT 0,
    label         TEXT    NOT NULL,
    actor         TEXT    NOT NULL,
    subject       TEXT    NOT NULL DEFAULT 'OUTGOING',
    custody_after TEXT    NOT NULL,
    picks_item    BOOLEAN NOT NULL DEFAULT FALSE,
    archived      BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (flow_id, position)
);

COMMENT ON TABLE ember_schema.movement_flow_step
    IS 'One step of a flow: who acknowledges it, which item it is about, and what custody that item is in afterwards.';
COMMENT ON COLUMN ember_schema.movement_flow_step.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.movement_flow_step.flow_id IS 'The flow this step belongs to.';
COMMENT ON COLUMN ember_schema.movement_flow_step.position IS 'Where in the chain the step sits, counted from zero.';
COMMENT ON COLUMN ember_schema.movement_flow_step.label IS 'What the step is called. Free text: the behaviour hangs off the custody and the subject, never off the words.';
COMMENT ON COLUMN ember_schema.movement_flow_step.actor IS 'Who acknowledges this step: MEMBER, STATION or OWNER.';
COMMENT ON COLUMN ember_schema.movement_flow_step.subject IS 'Which item the step is about: the OUTGOING one or the INCOMING one.';
COMMENT ON COLUMN ember_schema.movement_flow_step.custody_after IS 'The custody the subject item is in once the step is acknowledged.';
COMMENT ON COLUMN ember_schema.movement_flow_step.picks_item IS 'Whether the acknowledger names the replacement at this step. At most one incoming step per flow does.';
COMMENT ON COLUMN ember_schema.movement_flow_step.archived IS 'Whether the step is retired. A step in use is archived rather than deleted so finished movements still read with the words they were walked under.';

CREATE TABLE IF NOT EXISTS ember_schema.item_movement
(
    id               SERIAL PRIMARY KEY,
    station_id       INTEGER                  NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    purpose          TEXT                     NOT NULL,
    flow_id          INTEGER REFERENCES ember_schema.movement_flow (id) ON DELETE SET NULL,
    current_step_id  INTEGER REFERENCES ember_schema.movement_flow_step (id) ON DELETE SET NULL,
    member_id        INTEGER REFERENCES ember_schema.station_member (id) ON DELETE SET NULL,
    outgoing_item_id INTEGER REFERENCES ember_schema.inventory_item (id) ON DELETE SET NULL,
    incoming_item_id INTEGER REFERENCES ember_schema.inventory_item (id) ON DELETE SET NULL,
    inventory_id     INTEGER REFERENCES ember_schema.inventory (id) ON DELETE SET NULL,
    old_size_id      INTEGER REFERENCES ember_schema.inventory_size (id) ON DELETE SET NULL,
    new_size_id      INTEGER REFERENCES ember_schema.inventory_size (id) ON DELETE SET NULL,
    state            TEXT                     NOT NULL DEFAULT 'OPEN',
    reason           TEXT                     NOT NULL DEFAULT '',
    created_by       INTEGER REFERENCES ember_schema.station_member (id) ON DELETE SET NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    closed_at        TIMESTAMP WITH TIME ZONE,
    close_reason     TEXT
);

COMMENT ON TABLE ember_schema.item_movement
    IS 'One movement of gear between two parties, walking the flow pinned on it. Replaces the equipment exchange request, which could only ever be an exchange.';
COMMENT ON COLUMN ember_schema.item_movement.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.item_movement.station_id IS 'The station the movement runs at.';
COMMENT ON COLUMN ember_schema.item_movement.purpose IS 'What the movement is for: ISSUE, RETURN or EXCHANGE.';
COMMENT ON COLUMN ember_schema.item_movement.flow_id IS 'The flow this movement walks, resolved once at creation and pinned here.';
COMMENT ON COLUMN ember_schema.item_movement.current_step_id IS 'The step the movement is standing on, or null once it is closed.';
COMMENT ON COLUMN ember_schema.item_movement.member_id IS 'The member the movement starts or ends at, if any.';
COMMENT ON COLUMN ember_schema.item_movement.outgoing_item_id IS 'The item leaving, if any.';
COMMENT ON COLUMN ember_schema.item_movement.incoming_item_id IS 'The item arriving, once somebody has named it.';
COMMENT ON COLUMN ember_schema.item_movement.inventory_id IS 'The inventory the movement is about.';
COMMENT ON COLUMN ember_schema.item_movement.old_size_id IS 'The size being replaced, if any.';
COMMENT ON COLUMN ember_schema.item_movement.new_size_id IS 'The size asked for, if any.';
COMMENT ON COLUMN ember_schema.item_movement.state IS 'OPEN, DONE, DECLINED or CANCELLED.';
COMMENT ON COLUMN ember_schema.item_movement.reason IS 'Why the movement was started, in the words of whoever started it.';
COMMENT ON COLUMN ember_schema.item_movement.created_by IS 'Who started it, when that is somebody other than the member it concerns.';
COMMENT ON COLUMN ember_schema.item_movement.created_at IS 'When it was started.';
COMMENT ON COLUMN ember_schema.item_movement.closed_at IS 'When it reached its end, however it ended.';
COMMENT ON COLUMN ember_schema.item_movement.close_reason IS 'Why it was declined or cancelled, in the words of whoever ended it.';

CREATE TABLE IF NOT EXISTS ember_schema.item_movement_log
(
    id          SERIAL PRIMARY KEY,
    movement_id INTEGER                  NOT NULL REFERENCES ember_schema.item_movement (id) ON DELETE CASCADE,
    step_id     INTEGER REFERENCES ember_schema.movement_flow_step (id) ON DELETE SET NULL,
    step_label  TEXT                     NOT NULL,
    ack_kind    TEXT                     NOT NULL DEFAULT 'CONFIRMED',
    changed_by  INTEGER REFERENCES ember_schema.station_member (id) ON DELETE SET NULL,
    changed_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    note        TEXT                     NOT NULL DEFAULT ''
);

COMMENT ON TABLE ember_schema.item_movement_log
    IS 'What was acknowledged on a movement, when, by whom and on whose behalf.';
COMMENT ON COLUMN ember_schema.item_movement_log.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.item_movement_log.movement_id IS 'The movement this entry belongs to.';
COMMENT ON COLUMN ember_schema.item_movement_log.step_id IS 'The step acknowledged, or null once that step is gone.';
COMMENT ON COLUMN ember_schema.item_movement_log.step_label IS 'The words the step carried at the time, kept so a finished movement still reads the way it was walked.';
COMMENT ON COLUMN ember_schema.item_movement_log.ack_kind IS 'CONFIRMED when the party that owns the step said so itself, ASSERTED when the station said so for an owner that does not use Ember, FORCED when the flow owner overrode a party that could have answered and did not.';
COMMENT ON COLUMN ember_schema.item_movement_log.changed_by IS 'Who pressed the button.';
COMMENT ON COLUMN ember_schema.item_movement_log.changed_at IS 'When they pressed it.';
COMMENT ON COLUMN ember_schema.item_movement_log.note IS 'What they wrote alongside. Mandatory when the step was forced.';

CREATE TABLE IF NOT EXISTS ember_schema.movement_flow_binding
(
    station_id   INTEGER NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    inventory_id INTEGER REFERENCES ember_schema.inventory (id) ON DELETE CASCADE,
    owner_kind   TEXT    NOT NULL,
    purpose      TEXT    NOT NULL,
    flow_id      INTEGER NOT NULL REFERENCES ember_schema.movement_flow (id) ON DELETE CASCADE
);

-- Partial indexes rather than one plain UNIQUE, because a plain one treats nulls as distinct and
-- would happily accept two station-wide bindings for the same pair.
CREATE UNIQUE INDEX IF NOT EXISTS uq_movement_flow_binding_station
    ON ember_schema.movement_flow_binding (station_id, owner_kind, purpose) WHERE inventory_id IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_movement_flow_binding_inventory
    ON ember_schema.movement_flow_binding (inventory_id, owner_kind, purpose) WHERE inventory_id IS NOT NULL;

COMMENT ON TABLE ember_schema.movement_flow_binding
    IS 'Which flow a station uses for a given owner and purpose, per inventory when one is set and station-wide otherwise.';
COMMENT ON COLUMN ember_schema.movement_flow_binding.station_id IS 'The station the binding belongs to.';
COMMENT ON COLUMN ember_schema.movement_flow_binding.inventory_id IS 'The inventory this binding is for, or null for the station-wide one.';
COMMENT ON COLUMN ember_schema.movement_flow_binding.owner_kind IS 'Which owner the binding applies to: STATION or CLUSTER.';
COMMENT ON COLUMN ember_schema.movement_flow_binding.purpose IS 'Which purpose the binding applies to: ISSUE, RETURN or EXCHANGE.';
COMMENT ON COLUMN ember_schema.movement_flow_binding.flow_id IS 'The flow to walk.';

-- The custody column has been waiting for this table since the patch that added it.
ALTER TABLE ember_schema.inventory_item
    DROP CONSTRAINT IF EXISTS fk_inventory_item_custody_movement;

ALTER TABLE ember_schema.inventory_item
    ADD CONSTRAINT fk_inventory_item_custody_movement
        FOREIGN KEY (custody_movement_id) REFERENCES ember_schema.item_movement (id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_item_movement_station_state
    ON ember_schema.item_movement (station_id, state);
CREATE INDEX IF NOT EXISTS idx_item_movement_member
    ON ember_schema.item_movement (member_id) WHERE member_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_movement_flow_step_flow
    ON ember_schema.movement_flow_step (flow_id);

-- Presets, seeded and bound per station. A station edits, duplicates or adds to them; nothing below
-- is special to the code, they are ordinary rows.
--
-- Not one of them has a step belonging to the body above the station. These are the flows a station
-- reaches when that body does not run on this instance, and such a station knows exactly two things
-- about the parcel it posted: that it sent it, and that something came back. A step asking it to
-- tick "arrived at the owner" would be asking it to invent the event, and a recorded invention is
-- worse than no record at all. Gear posted away and never seen again settles with its owner when
-- the movement ends.
--
-- A body that does run here answers for itself, and the steps for its side belong to its own flow.
--
-- The old status chain mapped onto seven steps, so the migration below maps onto whatever these
-- presets actually contain rather than onto fixed positions.

INSERT INTO ember_schema.movement_flow (station_id, name, purpose)
SELECT s.id, v.name, v.purpose
FROM ember_schema.station s
CROSS JOIN (VALUES
    ('Tausch (Eigentum der Wache)', 'EXCHANGE'),
    ('Tausch (Eigentum des Trägers)', 'EXCHANGE'),
    ('Rückgabe an den Träger', 'RETURN'),
    ('Ausgabe durch den Träger', 'ISSUE')
) AS v(name, purpose);

INSERT INTO ember_schema.movement_flow_step (flow_id, position, label, actor, subject, custody_after, picks_item)
SELECT f.id, v.position, v.label, v.actor, v.subject, v.custody_after, v.picks_item
FROM ember_schema.movement_flow f
CROSS JOIN (VALUES
    (0, 'Tausch angekündigt', 'MEMBER', 'OUTGOING', 'WITH_MEMBER', FALSE),
    (1, 'Altes Teil zurückgenommen', 'STATION', 'OUTGOING', 'WITH_OWNER', FALSE),
    (2, 'Ersatz ausgegeben', 'STATION', 'INCOMING', 'WITH_MEMBER', TRUE)
) AS v(position, label, actor, subject, custody_after, picks_item)
WHERE f.station_id IS NOT NULL AND f.name = 'Tausch (Eigentum der Wache)';

INSERT INTO ember_schema.movement_flow_step (flow_id, position, label, actor, subject, custody_after, picks_item)
SELECT f.id, v.position, v.label, v.actor, v.subject, v.custody_after, v.picks_item
FROM ember_schema.movement_flow f
CROSS JOIN (VALUES
    (0, 'Tausch angekündigt', 'MEMBER', 'OUTGOING', 'WITH_MEMBER', FALSE),
    (1, 'Altes Teil zurückgenommen', 'STATION', 'OUTGOING', 'AT_STATION', FALSE),
    (2, 'An den Träger geschickt', 'STATION', 'OUTGOING', 'IN_TRANSIT', FALSE),
    (3, 'Ersatz erhalten', 'STATION', 'INCOMING', 'AT_STATION', TRUE),
    (4, 'Ersatz ausgegeben', 'STATION', 'INCOMING', 'WITH_MEMBER', FALSE)
) AS v(position, label, actor, subject, custody_after, picks_item)
WHERE f.station_id IS NOT NULL AND f.name = 'Tausch (Eigentum des Trägers)';

INSERT INTO ember_schema.movement_flow_step (flow_id, position, label, actor, subject, custody_after, picks_item)
SELECT f.id, v.position, v.label, v.actor, v.subject, v.custody_after, v.picks_item
FROM ember_schema.movement_flow f
CROSS JOIN (VALUES
    (0, 'Rückgabe angekündigt', 'STATION', 'OUTGOING', 'AT_STATION', FALSE),
    (1, 'An den Träger geschickt', 'STATION', 'OUTGOING', 'IN_TRANSIT', FALSE)
) AS v(position, label, actor, subject, custody_after, picks_item)
WHERE f.station_id IS NOT NULL AND f.name = 'Rückgabe an den Träger';

INSERT INTO ember_schema.movement_flow_step (flow_id, position, label, actor, subject, custody_after, picks_item)
SELECT f.id, v.position, v.label, v.actor, v.subject, v.custody_after, v.picks_item
FROM ember_schema.movement_flow f
CROSS JOIN (VALUES
    (0, 'Vom Träger erhalten', 'STATION', 'INCOMING', 'AT_STATION', TRUE)
) AS v(position, label, actor, subject, custody_after, picks_item)
WHERE f.station_id IS NOT NULL AND f.name = 'Ausgabe durch den Träger';

INSERT INTO ember_schema.movement_flow_binding (station_id, inventory_id, owner_kind, purpose, flow_id)
SELECT f.station_id, NULL, v.owner_kind, f.purpose, f.id
FROM ember_schema.movement_flow f
JOIN (VALUES
    ('Tausch (Eigentum der Wache)', 'STATION'),
    ('Tausch (Eigentum des Trägers)', 'CLUSTER'),
    ('Rückgabe an den Träger', 'CLUSTER'),
    ('Ausgabe durch den Träger', 'CLUSTER')
) AS v(name, owner_kind) ON v.name = f.name
WHERE f.station_id IS NOT NULL;

-- The exchange requests and their logs move into the two tables above and the old ones are dropped.
-- The old names cannot survive a table that also carries hand-ins and issues.
--
-- Each request walks the preset its item's owner points at, standing on the step its old status
-- corresponds to. Where the old chain had steps the shorter preset does not, the movement stands on
-- the last step that exists rather than jumping ahead of itself.
--
-- Custody is deliberately left alone. Nothing in the old record says which of the two items was in
-- the post at any moment, and asserting a guess would put gear in transit that nobody sent.

ALTER TABLE ember_schema.item_movement
    ADD COLUMN legacy_exchange_id INTEGER;

INSERT INTO ember_schema.item_movement (station_id, purpose, flow_id, current_step_id, member_id,
                                        outgoing_item_id, incoming_item_id, inventory_id, old_size_id,
                                        new_size_id, state, reason, created_by, created_at, closed_at,
                                        legacy_exchange_id)
SELECT r.station_id,
       'EXCHANGE',
       b.flow_id,
       step.id,
       r.member_id,
       r.item_id,
       r.exchanged_item_id,
       r.inventory_id,
       r.old_size_id,
       r.new_size_id,
       CASE WHEN r.status = 'DONE' THEN 'DONE' ELSE 'OPEN' END,
       r.reason,
       r.created_by,
       r.created_at,
       CASE WHEN r.status = 'DONE' THEN r.updated_at END,
       r.id
FROM ember_schema.equipment_exchange_request r
JOIN ember_schema.inventory inv ON inv.id = r.inventory_id
LEFT JOIN ember_schema.inventory_item ii ON ii.id = r.item_id
JOIN ember_schema.movement_flow_binding b
     ON b.station_id = r.station_id
    AND b.inventory_id IS NULL
    AND b.purpose = 'EXCHANGE'
    AND b.owner_kind = COALESCE(ii.owner_kind,
                                CASE WHEN inv.inventory_type = 'EXTERNAL' THEN 'CLUSTER' ELSE 'STATION' END)
CROSS JOIN LATERAL (SELECT count(*) AS n FROM ember_schema.movement_flow_step s WHERE s.flow_id = b.flow_id) len
LEFT JOIN LATERAL (
    SELECT s.id
    FROM ember_schema.movement_flow_step s
    WHERE s.flow_id = b.flow_id
      AND s.position = CASE
                           WHEN r.status = 'DONE' THEN -1
                           WHEN r.status = 'ANNOUNCED' THEN 0
                           WHEN len.n <= 3 THEN 1
                           WHEN r.status = 'RECEIVED' THEN 1
                           WHEN r.status = 'SHIPPED' THEN 2
                           WHEN r.status = 'ARRIVED' THEN 3
                           ELSE 0
                       END
) step ON TRUE;

INSERT INTO ember_schema.item_movement_log (movement_id, step_id, step_label, ack_kind, changed_by, changed_at, note)
SELECT m.id, NULL, l.new_status, 'CONFIRMED', l.changed_by, l.changed_at, l.note
FROM ember_schema.equipment_exchange_log l
JOIN ember_schema.item_movement m ON m.legacy_exchange_id = l.request_id;

ALTER TABLE ember_schema.item_movement
    DROP COLUMN legacy_exchange_id;

DROP TABLE IF EXISTS ember_schema.equipment_exchange_log;
DROP TABLE IF EXISTS ember_schema.equipment_exchange_request;


-- ============================================================

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


-- ============================================================

-- How a standing station joins a cluster.
--
-- A cluster never absorbs a station. Either the cluster created the station itself, in which case it belongs
-- from the first moment, or the station's owner asked to join and somebody at the cluster said yes. This
-- table is that second path, and only the station's owner may open one.
--
-- It is deliberately not the existing station_application table. That one is a stranger asking the instance
-- to found a station for them, and the two share neither their states nor their parties: this one can be
-- withdrawn by the applicant, that one can be waiting on an email nobody has clicked.

CREATE TABLE IF NOT EXISTS ember_schema.cluster_application
(
    id           SERIAL PRIMARY KEY,
    cluster_id   INTEGER                  NOT NULL REFERENCES ember_schema.cluster (id) ON DELETE CASCADE,
    station_id   INTEGER                  NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    requested_by INTEGER REFERENCES ember_schema.station_member (id) ON DELETE SET NULL,
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    status       TEXT                     NOT NULL DEFAULT 'PENDING',
    deny_reason  TEXT,
    resolved_at  TIMESTAMP WITH TIME ZONE,
    resolved_by  INTEGER REFERENCES ember_schema.cluster_member (id) ON DELETE SET NULL,
    UNIQUE (cluster_id, station_id)
);

COMMENT ON TABLE ember_schema.cluster_application
    IS 'A station owner asking to join a cluster. One row per station and cluster, whatever became of it.';
COMMENT ON COLUMN ember_schema.cluster_application.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.cluster_application.cluster_id IS 'The cluster being asked.';
COMMENT ON COLUMN ember_schema.cluster_application.station_id IS 'The station asking to join.';
COMMENT ON COLUMN ember_schema.cluster_application.requested_by
    IS 'The station member who asked, always the station owner at the time. Survives them leaving.';
COMMENT ON COLUMN ember_schema.cluster_application.requested_at IS 'When the application was opened.';
COMMENT ON COLUMN ember_schema.cluster_application.status
    IS 'PENDING while it waits, APPROVED, DENIED, or WITHDRAWN when the station took it back.';
COMMENT ON COLUMN ember_schema.cluster_application.deny_reason
    IS 'What the cluster said when it refused, shown to the station owner.';
COMMENT ON COLUMN ember_schema.cluster_application.resolved_at IS 'When it stopped being pending.';
COMMENT ON COLUMN ember_schema.cluster_application.resolved_by
    IS 'The cluster member who decided. Survives them leaving the cluster.';

CREATE INDEX IF NOT EXISTS idx_cluster_application_cluster ON ember_schema.cluster_application (cluster_id, status);
CREATE INDEX IF NOT EXISTS idx_cluster_application_station ON ember_schema.cluster_application (station_id);

-- A notification can now be addressed to a cluster member.
--
-- Until now every notification named a station member, because every recipient was one. A cluster member is
-- not: the people who run a cluster hold no membership in any of its stations, and inventing one for them
-- would put them in member lists where they do not belong. So the row names one of the two, never both and
-- never neither, and the queries that read a station member's feed keep working untouched because a cluster
-- row can never match them.

ALTER TABLE ember_schema.notification
    ALTER COLUMN member_id DROP NOT NULL;

ALTER TABLE ember_schema.notification
    ADD COLUMN IF NOT EXISTS cluster_member_id INTEGER REFERENCES ember_schema.cluster_member (id) ON DELETE CASCADE;

COMMENT ON COLUMN ember_schema.notification.member_id
    IS 'The station member this is for, or null when it is addressed to a cluster member instead.';
COMMENT ON COLUMN ember_schema.notification.cluster_member_id
    IS 'The cluster member this is for, or null when it is addressed to a station member instead.';

ALTER TABLE ember_schema.notification
    DROP CONSTRAINT IF EXISTS chk_notification_recipient;
ALTER TABLE ember_schema.notification
    ADD CONSTRAINT chk_notification_recipient CHECK (num_nonnulls(member_id, cluster_member_id) = 1);

CREATE INDEX IF NOT EXISTS idx_notification_cluster_member
    ON ember_schema.notification (cluster_member_id) WHERE cluster_member_id IS NOT NULL;


-- ============================================================

-- Federation pairs a cluster owns.
--
-- A cluster's content lives on its home station and reaches its member stations over the federation that
-- already exists. Nothing new carries it: same instance, same tables, same shares. What is new is that some
-- pairs are not the stations' own doing, so neither side may delete them, and one kind may not even be
-- paused.
--
-- Two kinds. The home pair runs between the home station and a member station in both directions, and it is
-- how cluster content arrives; without it the station would simply not see what its cluster publishes, so it
-- can be neither paused nor deleted. The mesh pairs run between member stations, and they exist because
-- stations under one roof usually want to see each other; either side may pause one, because that is a
-- matter between them.

ALTER TABLE ember_schema.federation_partner
    ADD COLUMN IF NOT EXISTS cluster_managed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS cluster_home    BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN ember_schema.federation_partner.cluster_managed
    IS 'TRUE when a cluster made this pair rather than the two stations. Neither side may delete it.';
COMMENT ON COLUMN ember_schema.federation_partner.cluster_home
    IS 'TRUE when this pair runs to or from a cluster home station, which carries the cluster content. It can never be paused.';

CREATE INDEX IF NOT EXISTS idx_federation_partner_cluster_managed
    ON ember_schema.federation_partner (station_id) WHERE cluster_managed;


-- ============================================================

-- What a cluster decides on behalf of its stations.
--
-- Three separate things, and the difference between them is the point. A denied module is a hard no: the
-- station cannot turn it on and does not get to argue. A look-and-feel setting is a starting point unless the
-- cluster marks it locked, so the usual case is that a station is handed something sensible and may still
-- change it. Storage is neither: the cluster is given a pool by the instance and hands portions of it out,
-- which is arithmetic rather than a rule.
--
-- Denying a module never deletes what the station already put in it. The module simply stops being
-- reachable, and everything reappears intact if the denial is lifted or the station released.

CREATE TABLE IF NOT EXISTS ember_schema.cluster_denied_module
(
    cluster_id INTEGER NOT NULL REFERENCES ember_schema.cluster (id) ON DELETE CASCADE,
    module     TEXT    NOT NULL,
    PRIMARY KEY (cluster_id, module)
);

COMMENT ON TABLE ember_schema.cluster_denied_module
    IS 'Modules a cluster switches off for all of its stations. A station cannot turn a denied module back on.';
COMMENT ON COLUMN ember_schema.cluster_denied_module.cluster_id IS 'The cluster doing the denying.';
COMMENT ON COLUMN ember_schema.cluster_denied_module.module
    IS 'The module name, matching the values a station uses for its own switched-off modules.';

-- The look a cluster hands its stations. The four locked flags already sit on the cluster row.

ALTER TABLE ember_schema.cluster
    ADD COLUMN IF NOT EXISTS default_theme       TEXT,
    ADD COLUMN IF NOT EXISTS custom_theme_colors TEXT,
    ADD COLUMN IF NOT EXISTS default_feel        TEXT;

COMMENT ON COLUMN ember_schema.cluster.default_theme
    IS 'The colour theme the cluster hands its stations, or null when it does not care.';
COMMENT ON COLUMN ember_schema.cluster.custom_theme_colors
    IS 'The cluster''s own colour set, in the same shape a station keeps its own.';
COMMENT ON COLUMN ember_schema.cluster.default_feel
    IS 'The interface feel the cluster hands its stations, or null when it does not care.';

-- A storage backend of the cluster's own, sitting between the station's override and the instance default.

CREATE TABLE IF NOT EXISTS ember_schema.cluster_storage_config
(
    cluster_id   INTEGER     NOT NULL PRIMARY KEY REFERENCES ember_schema.cluster (id) ON DELETE CASCADE,
    backend_type TEXT        NOT NULL,
    config       JSONB       NOT NULL DEFAULT '{}',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE ember_schema.cluster_storage_config
    IS 'A cluster''s own storage backend, used by its stations unless a station has an override of its own.';
COMMENT ON COLUMN ember_schema.cluster_storage_config.cluster_id IS 'The cluster this backend belongs to.';
COMMENT ON COLUMN ember_schema.cluster_storage_config.backend_type
    IS 'Discriminator for the typed backend configuration carried in config.';
COMMENT ON COLUMN ember_schema.cluster_storage_config.config
    IS 'The typed backend configuration, encrypted where it carries credentials.';
COMMENT ON COLUMN ember_schema.cluster_storage_config.created_at IS 'When the override was first set.';
COMMENT ON COLUMN ember_schema.cluster_storage_config.updated_at IS 'When it was last changed.';


-- ============================================================

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


-- ============================================================

-- Whether a cluster keeps its gear in Ember at all.
--
-- A cluster that runs here but does not use the inventory has nobody who can acknowledge anything about an
-- item: no store to post from, no queue to answer, no person whose job it is to confirm that a jacket came
-- back. Its stations then behave exactly as if there were no cluster above them, and their gear walks the
-- station's own flows, which carry no owner steps for precisely that reason.
--
-- The alternative would be a chain that stops on a step nobody will ever press, and a station left staring
-- at a movement waiting on a party that does not exist. Better to ask once.

ALTER TABLE ember_schema.cluster
    ADD COLUMN IF NOT EXISTS uses_inventory BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN ember_schema.cluster.uses_inventory
    IS 'TRUE when the cluster keeps its gear here, which is what lets its own steps appear in a movement.';


-- ============================================================

-- Losing a piece of gear, and asking the body above the station to replace it.
--
-- Marking something lost and reporting the loss are two acts rather than one. A station losing track of
-- a jacket is its own business until it wants a new one, so the marking travels nowhere; the report is
-- an ordinary exchange raised afterwards, and what it has to carry is the owner's to demand.

ALTER TABLE ember_schema.inventory_item
    ADD COLUMN IF NOT EXISTS lost_note    TEXT,
    ADD COLUMN IF NOT EXISTS lost_note_by INTEGER REFERENCES ember_schema.station_member (id) ON DELETE SET NULL;

COMMENT ON COLUMN ember_schema.inventory_item.lost_note
    IS 'What was written when the item was marked lost, cleared when it is found again.';
COMMENT ON COLUMN ember_schema.inventory_item.lost_note_by
    IS 'Who wrote that note, which is the guardian rather than the member when one acted for the other.';

ALTER TABLE ember_schema.station
    ADD COLUMN IF NOT EXISTS loss_note_required BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN ember_schema.station.loss_note_required
    IS 'Whether a member marking their own gear lost must write a note about it.';

ALTER TABLE ember_schema.cluster
    ADD COLUMN IF NOT EXISTS loss_report_requires TEXT NOT NULL DEFAULT 'NOTHING';

ALTER TABLE ember_schema.cluster
    DROP CONSTRAINT IF EXISTS chk_cluster_loss_report_requires;

ALTER TABLE ember_schema.cluster
    ADD CONSTRAINT chk_cluster_loss_report_requires
        CHECK (loss_report_requires IN ('NOTHING', 'NOTE', 'DOCUMENT'));

COMMENT ON COLUMN ember_schema.cluster.loss_report_requires
    IS 'What a loss report must carry before the association will look at it: NOTHING, NOTE or DOCUMENT.';

-- Evidence hangs off the movement rather than off the item or the member: it is evidence for this one
-- request, so opening the movement shows the report, both notes and the attachment in one place.
CREATE TABLE IF NOT EXISTS ember_schema.item_movement_document
(
    id          SERIAL PRIMARY KEY,
    movement_id INTEGER   NOT NULL REFERENCES ember_schema.item_movement (id) ON DELETE CASCADE,
    file_name   TEXT      NOT NULL,
    mime_type   TEXT      NOT NULL,
    size_bytes  BIGINT    NOT NULL,
    uploaded_by INTEGER REFERENCES ember_schema.station_member (id) ON DELETE SET NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_item_movement_document_movement
    ON ember_schema.item_movement_document (movement_id);

COMMENT ON TABLE ember_schema.item_movement_document
    IS 'A file attached to one movement as evidence, read by opening that movement.';
