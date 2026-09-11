-- Items say who owns them, and an inventory switched between internal and external after its items
-- were created never re-said it on them. A station-owned piece sitting in an external inventory was
-- refused a replacement by its own inventory, and it walked the station's chain on an exchange, so
-- the piece that had gone back to the body above the station stayed on the shelf as stock.
--
-- From now on switching an inventory rewrites its items, and this brings the rows written before
-- that rule into the same state. Internal inventories hold the station's gear, external ones the
-- gear of the body above the station, named when the station answers to one on this instance and
-- null when it does not. Mixed inventories hold both and are left alone, and borrowed gear keeps
-- its owner everywhere: a partner's radio stays the partner's whatever the shelf is called.

UPDATE ember_schema.inventory_item ii
SET owner_kind       = 'STATION',
    owner_cluster_id = NULL
FROM ember_schema.inventory i
WHERE i.id = ii.inventory_id
  AND i.inventory_type = 'INTERNAL'
  AND ii.owner_kind = 'CLUSTER';

UPDATE ember_schema.inventory_item ii
SET owner_kind       = 'CLUSTER',
    owner_cluster_id = s.cluster_id
FROM ember_schema.inventory i
         JOIN ember_schema.station s ON s.id = i.station_id
WHERE i.id = ii.inventory_id
  AND i.inventory_type = 'EXTERNAL'
  AND ii.owner_kind = 'STATION';

-- An exchange that finished while its piece still said the wrong owner walked the station's chain
-- and put the piece it had sent away back on the shelf, where every further exchange stacked one
-- more dead row. A finishing exchange removes the piece that went home to a body Ember cannot see,
-- and this removes the ones those finished exchanges left behind. A piece sent back on a return
-- keeps its row on purpose, because the row is the record of what was sent, and a piece on a
-- member's record, one marked lost and one still walking a movement all stay.
DELETE
FROM ember_schema.inventory_item ii
WHERE ii.owner_kind = 'CLUSTER'
  AND (ii.owner_cluster_id IS NULL OR EXISTS (SELECT 1
                                              FROM ember_schema.cluster c
                                              WHERE c.id = ii.owner_cluster_id
                                                AND NOT c.uses_inventory))
  AND ii.assigned_to IS NULL
  AND ii.custody NOT IN ('WITH_MEMBER', 'LOST')
  AND EXISTS (SELECT 1
              FROM ember_schema.item_movement done
              WHERE done.outgoing_item_id = ii.id
                AND done.purpose = 'EXCHANGE'
                AND done.state = 'DONE')
  AND NOT EXISTS (SELECT 1
                  FROM ember_schema.item_movement walking
                  WHERE (walking.outgoing_item_id = ii.id OR walking.incoming_item_id = ii.id)
                    AND walking.state = 'OPEN');
