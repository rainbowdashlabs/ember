-- Nothing is offered to a partner until somebody says so.
--
-- The table for it was created and never wired up: every partner saw every inventory a station had,
-- and the only filter that ever ran was a date block. A row says a whole inventory, a kind of thing
-- in it, or a single piece is on offer, to all partners or to named ones, and the narrowest row that
-- exists decides.
--
-- Taking one piece back out of a shared drawer is the case that comes up, so a row can withhold as
-- well as grant. Working that as "share the other things instead" would mean redoing the choice
-- every time something is added, and the one that was forgotten would silently be on offer.
--
-- One row per inventory, one per kind and one per piece, because two rows saying different things
-- about the same gear have no answer.

ALTER TABLE ember_schema.federation_inventory_share
    ADD COLUMN share_grant TEXT NOT NULL DEFAULT 'GRANT',
    ADD COLUMN art_id      INT REFERENCES ember_schema.inventory_art (id) ON DELETE CASCADE;

COMMENT ON COLUMN ember_schema.federation_inventory_share.share_grant IS
    'GRANT puts the gear on offer, WITHHOLD takes it back out of a wider offer. The narrowest row that exists decides.';
COMMENT ON COLUMN ember_schema.federation_inventory_share.art_id IS
    'References the kind of thing. Exactly one of inventory_id/art_id/item_id must be set.';

ALTER TABLE ember_schema.federation_inventory_share
    DROP CONSTRAINT federation_inventory_share_check;

ALTER TABLE ember_schema.federation_inventory_share
    ADD CONSTRAINT federation_inventory_share_one_level
        CHECK (num_nonnulls(inventory_id, art_id, item_id) = 1);

CREATE UNIQUE INDEX uq_federation_inventory_share_inventory
    ON ember_schema.federation_inventory_share (station_id, inventory_id)
    WHERE inventory_id IS NOT NULL;

CREATE UNIQUE INDEX uq_federation_inventory_share_art
    ON ember_schema.federation_inventory_share (station_id, art_id)
    WHERE art_id IS NOT NULL;

CREATE UNIQUE INDEX uq_federation_inventory_share_item
    ON ember_schema.federation_inventory_share (station_id, item_id)
    WHERE item_id IS NOT NULL;
