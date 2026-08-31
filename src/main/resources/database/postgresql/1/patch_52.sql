-- Nothing is offered to a partner until somebody says so.
--
-- The table for it was created and never wired up: every partner saw every inventory a station had,
-- and the only filter that ever ran was a date block. A row says a whole inventory or a single item
-- is on offer, to all partners or to named ones, and the narrowest row that exists decides.
--
-- Taking one item back out of a shared drawer is the case that comes up, so a row can withhold as
-- well as grant. Working that as "share the other things instead" would mean redoing the choice
-- every time something is added, and the one that was forgotten would silently be on offer.
--
-- One row per inventory and one per item, because two rows saying different things about the same
-- gear have no answer.

ALTER TABLE ember_schema.federation_inventory_share
    ADD COLUMN share_grant TEXT NOT NULL DEFAULT 'GRANT';

COMMENT ON COLUMN ember_schema.federation_inventory_share.share_grant IS
    'GRANT puts the gear on offer, WITHHOLD takes it back out of a wider offer. The narrowest row that exists decides.';

CREATE UNIQUE INDEX uq_federation_inventory_share_inventory
    ON ember_schema.federation_inventory_share (station_id, inventory_id)
    WHERE inventory_id IS NOT NULL;

CREATE UNIQUE INDEX uq_federation_inventory_share_item
    ON ember_schema.federation_inventory_share (station_id, item_id)
    WHERE item_id IS NOT NULL;
