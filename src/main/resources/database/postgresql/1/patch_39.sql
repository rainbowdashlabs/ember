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
