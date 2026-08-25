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

-- What a cluster decided about storage, which is a different fact from where any one station's bytes are.
--
-- Policy lives here and placement lives in cluster_station_storage, because a decision takes effect the
-- moment it is written and a copy of a terabyte does not. Read as one thing they come apart on the day of
-- the switch and stay apart for every station never moved.

ALTER TABLE ember_schema.cluster
    ADD COLUMN IF NOT EXISTS storage_backend_reach  TEXT    NOT NULL DEFAULT 'NONE',
    ADD COLUMN IF NOT EXISTS storage_backend_locked BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN ember_schema.cluster.storage_backend_reach
    IS 'How far the cluster''s own storage reaches: NONE, OWN_FILES for its own store alone, or EVERY_STATION.';
COMMENT ON COLUMN ember_schema.cluster.storage_backend_locked
    IS 'Whether a station may point itself anywhere; locked, only the cluster moves a station.';

-- A storage backend of the cluster's own, one row per version of it.
--
-- Versioned rather than singular because a station standing on the old destination would otherwise be
-- handed credentials for somewhere its bytes are not. A new destination is a new current version and
-- everybody on the old one is out of place; the old one stays readable until the last of them has left.

CREATE TABLE IF NOT EXISTS ember_schema.cluster_storage_config
(
    id           SERIAL PRIMARY KEY,
    cluster_id   INTEGER     NOT NULL REFERENCES ember_schema.cluster (id) ON DELETE CASCADE,
    backend_type TEXT        NOT NULL,
    config       JSONB       NOT NULL DEFAULT '{}',
    is_current   BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX IF NOT EXISTS cluster_storage_config_current
    ON ember_schema.cluster_storage_config (cluster_id) WHERE is_current;

COMMENT ON TABLE ember_schema.cluster_storage_config
    IS 'One version of a cluster''s own storage backend; at most one of them is current per cluster.';
COMMENT ON COLUMN ember_schema.cluster_storage_config.id IS 'The version a placement points at.';
COMMENT ON COLUMN ember_schema.cluster_storage_config.cluster_id IS 'The cluster this backend belongs to.';
COMMENT ON COLUMN ember_schema.cluster_storage_config.backend_type
    IS 'Discriminator for the typed backend configuration carried in config.';
COMMENT ON COLUMN ember_schema.cluster_storage_config.config
    IS 'The typed backend configuration, encrypted where it carries credentials.';
COMMENT ON COLUMN ember_schema.cluster_storage_config.is_current
    IS 'Whether this is the version the cluster points new placements at; kept false for the ones people still stand on.';
COMMENT ON COLUMN ember_schema.cluster_storage_config.created_at IS 'When this version was first set.';
COMMENT ON COLUMN ember_schema.cluster_storage_config.updated_at
    IS 'When its credentials were last edited, which is a change that moves nobody.';

-- Where one station's bytes actually are, when they are on a cluster's storage.
--
-- Absent for a station on its own backend or on the instance default, which is what the resolver reads and
-- what says who pays. config_id carries no ON DELETE clause on purpose: a version somebody is standing on
-- cannot be deleted, and the database is what says so rather than a comment.

CREATE TABLE IF NOT EXISTS ember_schema.cluster_station_storage
(
    station_id INTEGER     NOT NULL PRIMARY KEY REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    cluster_id INTEGER     NOT NULL REFERENCES ember_schema.cluster (id) ON DELETE CASCADE,
    config_id  INTEGER     NOT NULL REFERENCES ember_schema.cluster_storage_config (id),
    moved_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS cluster_station_storage_cluster ON ember_schema.cluster_station_storage (cluster_id);
CREATE INDEX IF NOT EXISTS cluster_station_storage_config ON ember_schema.cluster_station_storage (config_id);

COMMENT ON TABLE ember_schema.cluster_station_storage
    IS 'The stations whose bytes sit on a cluster''s storage, and which version of it they were carried to.';
COMMENT ON COLUMN ember_schema.cluster_station_storage.station_id IS 'The station whose bytes were moved.';
COMMENT ON COLUMN ember_schema.cluster_station_storage.cluster_id
    IS 'The cluster whose storage they are on, beside the version so the pool arithmetic is one indexed read.';
COMMENT ON COLUMN ember_schema.cluster_station_storage.config_id
    IS 'The version of that storage the bytes were carried to, which is what the resolver builds.';
COMMENT ON COLUMN ember_schema.cluster_station_storage.moved_at IS 'When the copy finished.';

-- The room a cluster hands out, in the same seven dimensions the instance uses.
--
-- The instance keeps its numbers on the station row and the cluster keeps its own here, because two parties
-- writing one column means neither can tell what the other did and the pool ends up counting a number nobody
-- handed out. What a station may use is resolved from the cluster's grant, then the cluster's defaults, then
-- what the instance says, so a station under a cluster is governed by the cluster and the instance's lever on
-- it is the pool.

ALTER TABLE ember_schema.cluster
    ADD COLUMN IF NOT EXISTS default_quota_bytes        BIGINT,
    ADD COLUMN IF NOT EXISTS default_quota_kb_bytes     BIGINT,
    ADD COLUMN IF NOT EXISTS default_quota_board_bytes  BIGINT,
    ADD COLUMN IF NOT EXISTS default_quota_images_bytes BIGINT,
    ADD COLUMN IF NOT EXISTS default_quota_pages_bytes  BIGINT,
    ADD COLUMN IF NOT EXISTS default_per_file_bytes     BIGINT,
    ADD COLUMN IF NOT EXISTS default_per_image_bytes    BIGINT;

COMMENT ON COLUMN ember_schema.cluster.default_quota_bytes
    IS 'How much a station of this cluster may use in total when the cluster granted it nothing of its own, or null to leave it to the instance.';
COMMENT ON COLUMN ember_schema.cluster.default_quota_kb_bytes
    IS 'The same, for knowledge base files and the documents filed beside them.';
COMMENT ON COLUMN ember_schema.cluster.default_quota_board_bytes IS 'The same, for board attachments.';
COMMENT ON COLUMN ember_schema.cluster.default_quota_images_bytes IS 'The same, for images.';
COMMENT ON COLUMN ember_schema.cluster.default_quota_pages_bytes IS 'The same, for page media.';
COMMENT ON COLUMN ember_schema.cluster.default_per_file_bytes IS 'The same, for the largest single file.';
COMMENT ON COLUMN ember_schema.cluster.default_per_image_bytes IS 'The same, for the largest single image.';

CREATE TABLE IF NOT EXISTS ember_schema.cluster_storage_quota_preset
(
    id         SERIAL PRIMARY KEY,
    cluster_id INTEGER NOT NULL REFERENCES ember_schema.cluster (id) ON DELETE CASCADE,
    name       TEXT    NOT NULL,
    total      BIGINT  NOT NULL,
    kb         BIGINT  NOT NULL,
    board      BIGINT  NOT NULL,
    images     BIGINT  NOT NULL,
    pages      BIGINT  NOT NULL,
    per_file   BIGINT  NOT NULL,
    per_image  BIGINT  NOT NULL,
    UNIQUE (cluster_id, name)
);

COMMENT ON TABLE ember_schema.cluster_storage_quota_preset
    IS 'A reusable set of quotas a cluster hands to its stations, the same shape as the instance''s own presets.';
COMMENT ON COLUMN ember_schema.cluster_storage_quota_preset.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.cluster_storage_quota_preset.cluster_id IS 'The cluster the preset belongs to.';
COMMENT ON COLUMN ember_schema.cluster_storage_quota_preset.name IS 'What the tier is called, unique within its cluster.';
COMMENT ON COLUMN ember_schema.cluster_storage_quota_preset.total IS 'Total room in bytes.';
COMMENT ON COLUMN ember_schema.cluster_storage_quota_preset.kb IS 'Room for knowledge base files in bytes.';
COMMENT ON COLUMN ember_schema.cluster_storage_quota_preset.board IS 'Room for board attachments in bytes.';
COMMENT ON COLUMN ember_schema.cluster_storage_quota_preset.images IS 'Room for images in bytes.';
COMMENT ON COLUMN ember_schema.cluster_storage_quota_preset.pages IS 'Room for page media in bytes.';
COMMENT ON COLUMN ember_schema.cluster_storage_quota_preset.per_file IS 'Largest single file in bytes.';
COMMENT ON COLUMN ember_schema.cluster_storage_quota_preset.per_image IS 'Largest single image in bytes.';

CREATE TABLE IF NOT EXISTS ember_schema.cluster_station_quota
(
    station_id         INTEGER PRIMARY KEY REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    cluster_id         INTEGER NOT NULL REFERENCES ember_schema.cluster (id) ON DELETE CASCADE,
    quota_bytes        BIGINT,
    quota_kb_bytes     BIGINT,
    quota_board_bytes  BIGINT,
    quota_images_bytes BIGINT,
    quota_pages_bytes  BIGINT,
    per_file_bytes     BIGINT,
    per_image_bytes    BIGINT,
    preset_id          INTEGER REFERENCES ember_schema.cluster_storage_quota_preset (id) ON DELETE SET NULL
);

COMMENT ON TABLE ember_schema.cluster_station_quota
    IS 'What one cluster granted one of its stations. A station without a row here has been granted nothing and falls back to the cluster''s defaults.';
COMMENT ON COLUMN ember_schema.cluster_station_quota.station_id IS 'The station being granted room.';
COMMENT ON COLUMN ember_schema.cluster_station_quota.cluster_id IS 'The cluster granting it, carried here so the pool arithmetic is one read.';
COMMENT ON COLUMN ember_schema.cluster_station_quota.quota_bytes IS 'Total room in bytes, or null to fall back to the cluster''s default.';
COMMENT ON COLUMN ember_schema.cluster_station_quota.quota_kb_bytes IS 'Room for knowledge base files in bytes, or null to fall back.';
COMMENT ON COLUMN ember_schema.cluster_station_quota.quota_board_bytes IS 'Room for board attachments in bytes, or null to fall back.';
COMMENT ON COLUMN ember_schema.cluster_station_quota.quota_images_bytes IS 'Room for images in bytes, or null to fall back.';
COMMENT ON COLUMN ember_schema.cluster_station_quota.quota_pages_bytes IS 'Room for page media in bytes, or null to fall back.';
COMMENT ON COLUMN ember_schema.cluster_station_quota.per_file_bytes IS 'Largest single file in bytes, or null to fall back.';
COMMENT ON COLUMN ember_schema.cluster_station_quota.per_image_bytes IS 'Largest single image in bytes, or null to fall back.';
COMMENT ON COLUMN ember_schema.cluster_station_quota.preset_id
    IS 'The cluster tier the station was put on, kept so the screen can name it. Editing a tier does not move the stations already on it.';

CREATE INDEX IF NOT EXISTS idx_cluster_station_quota_cluster
    ON ember_schema.cluster_station_quota (cluster_id);


-- ============================================================

-- Questions a cluster asks of the people at its stations.
--
-- A station already decides what it wants to know about its members. A cluster has questions of its own, and
-- they are the same kind of thing: a name, a type, a position and a place in the profile. So the shape here
-- mirrors profile_field almost exactly, and the value hangs off the same station_member row, because the
-- person being asked is a member of a station and not of the cluster.
--
-- An association's stations do different work, and a question sensible at one is noise at the next.
-- A group is a filing of stations rather than a partition of them: a station can sit in a regional
-- group and an equipment group at once, because those are two different questions about it.

CREATE TABLE IF NOT EXISTS ember_schema.cluster_station_group
(
    id         SERIAL PRIMARY KEY,
    cluster_id INTEGER NOT NULL REFERENCES ember_schema.cluster (id) ON DELETE CASCADE,
    name       TEXT    NOT NULL,
    UNIQUE (cluster_id, name)
);

COMMENT ON TABLE ember_schema.cluster_station_group
    IS 'A named set of an association''s stations, which its questions can be pointed at.';
COMMENT ON COLUMN ember_schema.cluster_station_group.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.cluster_station_group.cluster_id IS 'The association doing the filing.';
COMMENT ON COLUMN ember_schema.cluster_station_group.name IS 'The label, unique within its association.';

CREATE TABLE IF NOT EXISTS ember_schema.cluster_station_group_membership
(
    group_id   INTEGER NOT NULL REFERENCES ember_schema.cluster_station_group (id) ON DELETE CASCADE,
    station_id INTEGER NOT NULL REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    PRIMARY KEY (group_id, station_id)
);

COMMENT ON TABLE ember_schema.cluster_station_group_membership
    IS 'Which stations are in which group. A station may be in several, because the ways of grouping stations cut across each other.';
COMMENT ON COLUMN ember_schema.cluster_station_group_membership.group_id IS 'The group.';
COMMENT ON COLUMN ember_schema.cluster_station_group_membership.station_id IS 'The station in it.';

CREATE INDEX IF NOT EXISTS idx_cluster_station_group_membership_station
    ON ember_schema.cluster_station_group_membership (station_id);

-- A denial can now name a group instead of everybody. Null keeps what the column meant before it
-- existed, every station of the cluster, so every row already written goes on meaning what it meant.
--
-- Denials add up and never cancel: a module is unreachable at a station when the cluster denies it
-- outright or denies it for any group that station is in. There is deliberately no way to permit
-- something for a group that is denied for everybody, because a permission that beats a denial is a
-- second rule and the screen would have to explain which of the two wins.
--
-- RESTRICT rather than CASCADE, because dropping a way of grouping stations must not quietly switch
-- modules back on at every station that was in it.

ALTER TABLE ember_schema.cluster_denied_module
    ADD COLUMN IF NOT EXISTS station_group_id INTEGER
        REFERENCES ember_schema.cluster_station_group (id) ON DELETE RESTRICT;

COMMENT ON COLUMN ember_schema.cluster_denied_module.station_group_id
    IS 'The group of stations the denial applies to, or null for every station of the cluster.';

-- A unique constraint rather than the primary key it replaces: a primary key column cannot be null,
-- and null is exactly what "denied for everybody" is written as. NULLS NOT DISTINCT is what makes two
-- rows denying the same module to everybody a collision rather than two separate facts.

ALTER TABLE ember_schema.cluster_denied_module
    DROP CONSTRAINT IF EXISTS cluster_denied_module_pkey;

ALTER TABLE ember_schema.cluster_denied_module
    ADD CONSTRAINT cluster_denied_module_key
        UNIQUE NULLS NOT DISTINCT (cluster_id, module, station_group_id);

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
    station_group_id INTEGER REFERENCES ember_schema.cluster_station_group (id) ON DELETE RESTRICT,
    UNIQUE NULLS NOT DISTINCT (cluster_id, scope, station_group_id, name)
);

COMMENT ON TABLE ember_schema.cluster_profile_field
    IS 'A question a cluster asks of the members at its stations, shaped like a station''s own profile field.';
COMMENT ON COLUMN ember_schema.cluster_profile_field.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.cluster_profile_field.cluster_id IS 'The cluster asking.';
COMMENT ON COLUMN ember_schema.cluster_profile_field.name
    IS 'The label, unique within its cluster, its scope and the group of stations it is asked of.';
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
COMMENT ON COLUMN ember_schema.cluster_profile_field.station_group_id
    IS 'The group of stations this question is asked of. NULL asks it of every station under the association.';

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

-- A report that a piece of gear is gone, raised as an exchange because that is what it is: the owner is
-- being asked for a replacement. What it lacks is the return leg, since there is nothing to walk back.
ALTER TABLE ember_schema.item_movement
    ADD COLUMN IF NOT EXISTS lost_report BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN ember_schema.item_movement.lost_report
    IS 'TRUE when the movement was raised to report gear missing, which is what makes it skip the return leg.';

-- The pieces a movement carries beyond the one it names.
--
-- An association sends a station twenty jackets at once and the station confirms one arrival, not twenty,
-- so the movement has to be able to hold a set. The named outgoing and incoming items stay what a movement
-- points at, and everything already reading one is untouched.
CREATE TABLE IF NOT EXISTS ember_schema.item_movement_item
(
    movement_id INTEGER NOT NULL REFERENCES ember_schema.item_movement (id) ON DELETE CASCADE,
    item_id     INTEGER NOT NULL REFERENCES ember_schema.inventory_item (id) ON DELETE CASCADE,
    subject     TEXT    NOT NULL,
    PRIMARY KEY (movement_id, item_id)
);

CREATE INDEX IF NOT EXISTS idx_item_movement_item_item
    ON ember_schema.item_movement_item (item_id);

COMMENT ON TABLE ember_schema.item_movement_item
    IS 'The pieces one movement carries, for a dispatch that sends many at once.';
COMMENT ON COLUMN ember_schema.item_movement_item.subject
    IS 'Which leg the piece is on: OUTGOING or INCOMING, the same distinction a step draws.';

-- An order need not be for anybody.
--
-- An association buys for its own store and hands out later, so the person is a station's detail rather
-- than something every order has. Existing rows all name one and keep it.
ALTER TABLE ember_schema.equipment_procurement
    ALTER COLUMN member_id DROP NOT NULL;

-- A change to a managed member's access that has been made but not yet announced.
--
-- A guardian switching signing in on or off tells the member by mail, and the mail waits a few minutes
-- rather than leaving at once: a switch flicked by accident and flicked straight back should reach
-- nobody. What waits here is the state to announce, not the message, so the mail that is finally sent is
-- decided from the account as it stands when the wait is over.
--
-- One row per member, because only the newest change is worth telling anybody about.
CREATE TABLE IF NOT EXISTS ember_schema.managed_login_notice
(
    member_id INTEGER PRIMARY KEY REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    granted   BOOLEAN     NOT NULL,
    due_at    TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_managed_login_notice_due
    ON ember_schema.managed_login_notice (due_at);

COMMENT ON TABLE ember_schema.managed_login_notice
    IS 'Access changes a guardian made that the member has not been told about yet. Emptied by the sweeper that sends the mails.';
COMMENT ON COLUMN ember_schema.managed_login_notice.member_id
    IS 'The member the change was made for, who is also the recipient of the mail.';
COMMENT ON COLUMN ember_schema.managed_login_notice.granted
    IS 'The state waiting to be announced: TRUE when signing in was switched on, FALSE when it was taken away. A switch back to the other value inside the waiting time deletes the row instead of replacing it.';
COMMENT ON COLUMN ember_schema.managed_login_notice.due_at
    IS 'When the mail may leave, which is the moment of the change plus the configured waiting time.';

-- The name somebody signs in with, when it is not their address.
--
-- A guardian can give a member in their care a name to sign in with, which is what makes a login
-- possible for somebody who has no address of their own at all. Anybody else may have one too, as a
-- second way in beside the address they still have to have.
--
-- A name never contains '@', so it can never be mistaken for an address and never collide with one.
-- Uniqueness is therefore name against name, and it is judged without regard to case.
ALTER TABLE ember_schema.account
    ADD COLUMN IF NOT EXISTS username TEXT;

CREATE UNIQUE INDEX IF NOT EXISTS account_username_key
    ON ember_schema.account (lower(username)) WHERE username IS NOT NULL;

COMMENT ON COLUMN ember_schema.account.username
    IS 'The name this account signs in with beside its address, or null when the address is the only way in. Never contains an at sign, and is unique without regard to case. A station arriving from another instance whose name is already taken here arrives without one.';

-- A requirement of the association's can name a group of stations instead of all of them.
--
-- An association writes one requirement and every station under it counts against it, which is right for
-- a jacket and wrong for a boat. Naming a group is how it says where a requirement applies, and null
-- keeps what the column meant before it existed: every station of the association.
--
-- Only an association's requirement ever names one. A station writing its own is not in a position to
-- group anything, and the column stays null on every row it writes.
--
-- RESTRICT for the same reason the denials use it: dropping a way of grouping stations must not quietly
-- widen a requirement to every station that was in the group.
ALTER TABLE ember_schema.inventory_requirement
    ADD COLUMN IF NOT EXISTS station_group_id INTEGER
        REFERENCES ember_schema.cluster_station_group (id) ON DELETE RESTRICT;

COMMENT ON COLUMN ember_schema.inventory_requirement.station_group_id
    IS 'The group of stations this requirement counts at, or null for every station reading it.';

CREATE INDEX IF NOT EXISTS idx_inventory_requirement_station_group
    ON ember_schema.inventory_requirement (station_group_id);

CREATE TABLE IF NOT EXISTS ember_schema.event_deadline_reminder_sent
(
    event_id    INTEGER     NOT NULL
        REFERENCES ember_schema.station_event (id) ON DELETE CASCADE,
    days_before INTEGER     NOT NULL,
    sent_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (event_id, days_before)
);

COMMENT ON TABLE ember_schema.event_deadline_reminder_sent
    IS 'Which run-out warnings have gone out for an event, so a sweep every few minutes warns once.';

COMMENT ON COLUMN ember_schema.event_deadline_reminder_sent.days_before
    IS 'How many days before the registration deadline this warning was the one for.';


-- What somebody said about an onboarding task, on the three levels a task can belong to.
--
-- Whether a task is done is derived from the thing itself and never stored, because a stored tick
-- goes on claiming something that has since been undone. Only what somebody said is kept: CONFIRMED
-- for what nothing in the database can see, SKIPPED for a task passed over, and taking a skip back
-- is deleting the row.
--
-- Three tables rather than one with an owner column, because the owner is what makes them different,
-- and its foreign key is what clears the rows away when it goes.

CREATE TABLE IF NOT EXISTS ember_schema.onboarding_member_task
(
    member_id  INTEGER     NOT NULL
        REFERENCES ember_schema.station_member (id) ON DELETE CASCADE,
    task_key   TEXT        NOT NULL,
    state      TEXT        NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (member_id, task_key),
    CONSTRAINT chk_onboarding_member_task_state CHECK (state IN ('CONFIRMED', 'SKIPPED', 'DISMISSED'))
);

COMMENT ON TABLE ember_schema.onboarding_member_task
    IS 'What a member said about their own onboarding tasks. Holds only what cannot be derived: a task ticked off by hand, or one deliberately skipped.';
COMMENT ON COLUMN ember_schema.onboarding_member_task.member_id IS 'The member whose task this is. Their own, shared with nobody.';
COMMENT ON COLUMN ember_schema.onboarding_member_task.task_key IS 'Which task, by the key the catalogue gives it. Carries the member it is about where a task repeats per managed member.';
COMMENT ON COLUMN ember_schema.onboarding_member_task.state IS 'CONFIRMED for a task ticked off by hand, SKIPPED for one passed over, DISMISSED for one thrown away for good. A task taken up again has its row deleted.';
COMMENT ON COLUMN ember_schema.onboarding_member_task.changed_at IS 'When it was last said.';

CREATE TABLE IF NOT EXISTS ember_schema.onboarding_station_task
(
    station_id        INTEGER     NOT NULL
        REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    task_key          TEXT        NOT NULL,
    state             TEXT        NOT NULL,
    changed_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    changed_by_member INTEGER     REFERENCES ember_schema.station_member (id) ON DELETE SET NULL,
    PRIMARY KEY (station_id, task_key),
    CONSTRAINT chk_onboarding_station_task_state CHECK (state IN ('CONFIRMED', 'SKIPPED', 'DISMISSED'))
);

COMMENT ON TABLE ember_schema.onboarding_station_task
    IS 'What a station said about setting itself up. Shared by everyone who manages the station: what one of them ticks off is ticked off for all.';
COMMENT ON COLUMN ember_schema.onboarding_station_task.station_id IS 'The station whose setup this is about.';
COMMENT ON COLUMN ember_schema.onboarding_station_task.task_key IS 'Which task, by the key the catalogue gives it.';
COMMENT ON COLUMN ember_schema.onboarding_station_task.state IS 'CONFIRMED for a task ticked off by hand, SKIPPED for one passed over, DISMISSED for one thrown away for good. A task taken up again has its row deleted.';
COMMENT ON COLUMN ember_schema.onboarding_station_task.changed_at IS 'When it was last said.';
COMMENT ON COLUMN ember_schema.onboarding_station_task.changed_by_member IS 'Who said it, so a colleague can see whose decision they are looking at. Null once that member is gone.';

CREATE TABLE IF NOT EXISTS ember_schema.onboarding_instance_task
(
    task_key           TEXT        PRIMARY KEY,
    state              TEXT        NOT NULL,
    changed_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    changed_by_account INTEGER     REFERENCES ember_schema.account (id) ON DELETE SET NULL,
    CONSTRAINT chk_onboarding_instance_task_state CHECK (state IN ('CONFIRMED', 'SKIPPED', 'DISMISSED'))
);

COMMENT ON TABLE ember_schema.onboarding_instance_task
    IS 'What an administrator said about setting up the instance. Shared by every administrator, because the instance is set up once and not once per person.';
COMMENT ON COLUMN ember_schema.onboarding_instance_task.task_key IS 'Which task, by the key the catalogue gives it.';
COMMENT ON COLUMN ember_schema.onboarding_instance_task.state IS 'CONFIRMED for a task ticked off by hand, SKIPPED for one passed over, DISMISSED for one thrown away for good. A task taken up again has its row deleted.';
COMMENT ON COLUMN ember_schema.onboarding_instance_task.changed_at IS 'When it was last said.';
COMMENT ON COLUMN ember_schema.onboarding_instance_task.changed_by_account IS 'Who said it, so another administrator can see whose decision they are looking at. Null once that account is gone.';

-- Where a question catalog came from.
--
-- A catalog travels: it is exported to a file, handed to another station and imported there. Until
-- now it arrived anonymous, and the station that received it had no way to say who wrote the
-- questions or under what terms they may be used. These four columns travel with it in the export
-- file and are filled in on import.

ALTER TABLE ember_schema.quiz_catalog ADD COLUMN IF NOT EXISTS language TEXT;
ALTER TABLE ember_schema.quiz_catalog ADD COLUMN IF NOT EXISTS source TEXT;
ALTER TABLE ember_schema.quiz_catalog ADD COLUMN IF NOT EXISTS author TEXT;
ALTER TABLE ember_schema.quiz_catalog ADD COLUMN IF NOT EXISTS license TEXT;

COMMENT ON COLUMN ember_schema.quiz_catalog.language IS 'The language the questions are written in, as a BCP 47 tag. Null when nobody said.';
COMMENT ON COLUMN ember_schema.quiz_catalog.source IS 'Where the questions came from, in free text: the sheet, the handbook or the station they were taken over from. Null when nobody said.';
COMMENT ON COLUMN ember_schema.quiz_catalog.author IS 'Who wrote the questions, in free text. Null when nobody said.';
COMMENT ON COLUMN ember_schema.quiz_catalog.license IS 'The terms the questions may be used under, in free text. Null when nobody said.';

-- What a member said is wrong with a question.
--
-- Whoever trains against a catalog is the one who notices that an answer is out of date, that two
-- options are both defensible, or that the question reads two ways. Until now there was nowhere to
-- put that: the member had to remember it and find whoever maintains the catalog. A note lands here
-- instead, is shown on the question in the catalog, and is deleted once somebody has dealt with it.

CREATE TABLE IF NOT EXISTS ember_schema.quiz_question_report
(
    id          SERIAL PRIMARY KEY,
    question_id INTEGER     NOT NULL REFERENCES ember_schema.quiz_question (id) ON DELETE CASCADE,
    reported_by INTEGER     REFERENCES ember_schema.station_member (id) ON DELETE SET NULL,
    note        TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_quiz_question_report_question ON ember_schema.quiz_question_report (question_id);

COMMENT ON TABLE ember_schema.quiz_question_report
    IS 'Notes members left on a quiz question while training, saying that something about it is wrong, out of date or ambiguous. Deleted once whoever maintains the catalog has acknowledged the note.';
COMMENT ON COLUMN ember_schema.quiz_question_report.id IS 'Auto-generated primary key.';
COMMENT ON COLUMN ember_schema.quiz_question_report.question_id IS 'The question the note is about. The note goes when the question goes.';
COMMENT ON COLUMN ember_schema.quiz_question_report.reported_by IS 'Who wrote the note, so somebody can ask back. Null once that member is gone, which leaves the note itself standing.';
COMMENT ON COLUMN ember_schema.quiz_question_report.note IS 'What the member says is wrong with the question, in their own words.';
COMMENT ON COLUMN ember_schema.quiz_question_report.created_at IS 'When the note was written.';
