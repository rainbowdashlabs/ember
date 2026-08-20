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
