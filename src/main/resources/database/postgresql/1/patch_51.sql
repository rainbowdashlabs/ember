-- Where borrowed gear lives at the station that borrowed it.
--
-- Until now a borrowed piece had no row at the borrower at all. Lending wrote on the owner's row
-- and set it to WITH_PARTNER, and that was the whole of it, so the borrower could not put the piece
-- in a container, hand it to a member, walk it in a check or count it towards anything: there was
-- nothing to point at. The borrower's only view was a lending request in status LENT, which is a
-- process rather than a thing.
--
-- Three changes, and they only mean something together:
--
--   1. A third owner kind, so a borrowed piece can be an ordinary row that says whose it is.
--   2. A loan reference on that row, which is what pairs it with the owner's row and what makes it
--      disappear again when the gear goes home.
--   3. A borrowed inventory per station, and a partner named on the owner's own row.


-- A partner station is a third owner kind.
--
-- owner_station_id names the owning station, symmetric to owner_cluster_id naming the owning body.
-- The reason for putting it on the ownership axis is what it inherits: a station may not edit, lend
-- or delete gear it does not own, and both of those rules already read owner_kind, so they cover
-- borrowed gear from the first day without a second case written anywhere.
--
-- Both references delete the row rather than emptying it. A borrowed row is a copy of somebody
-- else's gear taken for the length of one loan; without the loan or without the owner it is not a
-- row waiting to be re-homed, it is a row about nothing.

ALTER TABLE ember_schema.inventory_item
    ADD COLUMN IF NOT EXISTS owner_station_id     INTEGER
        REFERENCES ember_schema.station (id) ON DELETE CASCADE,
    ADD COLUMN IF NOT EXISTS loan_request_item_id INTEGER
        REFERENCES ember_schema.federation_lending_request_item (id) ON DELETE CASCADE;

ALTER TABLE ember_schema.inventory_item
    DROP CONSTRAINT IF EXISTS chk_inventory_item_owner;

-- Each owner kind forbids the pointers that do not belong to it, and PARTNER_STATION requires the
-- two that do: a borrowed row always knows whose the gear is and which loan it came on.
ALTER TABLE ember_schema.inventory_item
    ADD CONSTRAINT chk_inventory_item_owner CHECK (
        CASE owner_kind
            WHEN 'STATION' THEN owner_cluster_id IS NULL
                AND owner_station_id IS NULL AND loan_request_item_id IS NULL
            WHEN 'CLUSTER' THEN owner_station_id IS NULL AND loan_request_item_id IS NULL
            WHEN 'PARTNER_STATION' THEN owner_cluster_id IS NULL
                AND owner_station_id IS NOT NULL AND loan_request_item_id IS NOT NULL
            ELSE FALSE
        END
    );

CREATE INDEX IF NOT EXISTS idx_inventory_item_loan_request_item
    ON ember_schema.inventory_item (loan_request_item_id)
    WHERE loan_request_item_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_inventory_item_owner_station
    ON ember_schema.inventory_item (owner_station_id)
    WHERE owner_station_id IS NOT NULL;

COMMENT ON COLUMN ember_schema.inventory_item.owner_station_id
    IS 'The station that owns the item when a partner station does, null for every other owner. Only ever set for PARTNER_STATION.';
COMMENT ON COLUMN ember_schema.inventory_item.loan_request_item_id
    IS 'The line of the lending request this borrowed copy came in on, which is what pairs it with the owner''s row and what ends it when the gear goes home. Only ever set for PARTNER_STATION.';


-- Which partner has it, on the owner's own row.
--
-- The custody columns said an item was with a partner but not which one, and that fact lived in the
-- lending request, one join away from anything asking where a radio is. custody_station_id keeps
-- naming the lender, because that is what puts the piece in the lender's own lists; the partner
-- holding it gets a column of its own.
--
-- This one empties rather than deletes. A partner station going away leaves gear recorded as being
-- somewhere nobody can name any more, which is a row waiting to be dealt with rather than a row
-- about nothing.

ALTER TABLE ember_schema.inventory_item
    ADD COLUMN IF NOT EXISTS custody_partner_station_id INTEGER
        REFERENCES ember_schema.station (id) ON DELETE SET NULL;

UPDATE ember_schema.inventory_item ii
SET custody_partner_station_id = s.id
FROM ember_schema.federation_lending_request_item ri
         JOIN ember_schema.federation_lending_request r ON r.id = ri.request_id
         JOIN ember_schema.station s ON s.uid = r.requesting_station_uid
WHERE ri.assigned_item_id = ii.id
  AND ii.custody = 'WITH_PARTNER';

ALTER TABLE ember_schema.inventory_item
    DROP CONSTRAINT IF EXISTS chk_inventory_item_custody_partner;

ALTER TABLE ember_schema.inventory_item
    ADD CONSTRAINT chk_inventory_item_custody_partner
        CHECK (custody = 'WITH_PARTNER' OR custody_partner_station_id IS NULL);

COMMENT ON COLUMN ember_schema.inventory_item.custody_partner_station_id
    IS 'The federation partner holding the item while it is WITH_PARTNER, null for every other custody. Null while it stands only when that station has since been removed.';


-- One borrowed inventory per station, created when it is first needed.
--
-- Everything belonging to somebody else lands in it, whichever partner it came from, because that
-- is the question a station actually asks: what have we got here at the moment that is not ours.
-- Split by partner, that question needs several screens read together and every one-off loan leaves
-- an empty shell behind for good.
--
-- It is heterogeneous by construction, so it can never be used for a requirement or a procurement.
-- The station may rename it, and the partial unique index is what keeps there being only one.

ALTER TABLE ember_schema.inventory
    ADD COLUMN IF NOT EXISTS borrowed BOOLEAN NOT NULL DEFAULT FALSE;

CREATE UNIQUE INDEX IF NOT EXISTS idx_inventory_borrowed_per_station
    ON ember_schema.inventory (station_id)
    WHERE borrowed;

COMMENT ON COLUMN ember_schema.inventory.borrowed
    IS 'Whether this is the station''s one shelf for gear belonging to somebody else. Created on the first handover, renameable, and refused deletion while anything is still on it.';
