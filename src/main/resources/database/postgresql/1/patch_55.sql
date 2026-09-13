-- Movements: gear that can be recognised, the word for the thing itself, and when one last moved.
--
-- Three changes that ship together, so they are one file: nothing here has been applied anywhere yet.
--
-- 1. An inventory and a kind inside one can carry a picture and a colour.
--
-- Every list of gear is a list of words today: nine inventories of one station are nine identical rows,
-- and a piece is told from another only by reading it. A stock of one thing in many copies is the thing,
-- so its picture belongs on the inventory; a drawer of different things has no single picture, so the
-- picture belongs on the kinds inside it. Both are nullable, and null means the reader falls back: the
-- kind first, then the inventory, then a plain shape. That is what makes this additive, and it is why no
-- row needs writing here.
--
-- The icon is a FontAwesome name out of the catalogue the frontend offers. The name is not checked in
-- the database and not checked on the way in either, because the catalogue belongs to the frontend and a
-- picture it has retired must not make a save fail. The colour is checked, because an unparseable one
-- would be painted.

ALTER TABLE ember_schema.inventory
    ADD COLUMN icon  TEXT,
    ADD COLUMN color TEXT;

COMMENT ON COLUMN ember_schema.inventory.icon IS
    'The FontAwesome name drawn for every piece of this inventory, or null to fall back to a plain shape. A stock of one thing in many copies is the thing it holds, which is what makes one picture right for all of it.';
COMMENT ON COLUMN ember_schema.inventory.color IS
    'The colour that picture is drawn in, as #rrggbb, or null for the muted neutral. The colour sits on the glyph and never behind text, so it is never the thing a word has to be read against.';

ALTER TABLE ember_schema.inventory_art
    ADD COLUMN icon  TEXT,
    ADD COLUMN color TEXT;

COMMENT ON COLUMN ember_schema.inventory_art.icon IS
    'The FontAwesome name drawn for the pieces of this kind, or null to fall back to the inventory. A drawer of different things has no one picture and the kind is the row that does.';
COMMENT ON COLUMN ember_schema.inventory_art.color IS
    'The colour that picture is drawn in, as #rrggbb, or null to fall back to the inventory.';

ALTER TABLE ember_schema.inventory_container_kind
    ADD COLUMN color TEXT;

COMMENT ON COLUMN ember_schema.inventory_container_kind.color IS
    'The colour the kind icon is drawn in, as #rrggbb, or null for the muted neutral. The icon itself has been here since the containers were built and keeps its default.';

-- 2. Every movement of gear is a movement now, and only one of the four kinds was ever an exchange.
--
-- The queue holds issues, returns, swaps and requests, and it always did: the exchange list showed every
-- one of them and called them all exchanges. The permission that opens it, and the two notification types
-- it sends, said the same thing and were as wrong.
--
-- The permission rows are the lookup table, so a grant keeps its row and nobody loses anything. The
-- notification rows are read back through an enum, so a row still carrying the old word would refuse to
-- load at all: those are rewritten, and so is the page a notification links to.

UPDATE ember_schema.station_permission
   SET name = 'INVENTORY_MOVEMENTS'
 WHERE name = 'INVENTORY_EXCHANGE';

UPDATE ember_schema.cluster_permission
   SET name = 'CLUSTER_INVENTORY_MOVEMENTS'
 WHERE name = 'CLUSTER_INVENTORY_EXCHANGE';

UPDATE ember_schema.user_notification_settings
   SET notification_type = 'MOVEMENT_RAISED'
 WHERE notification_type = 'EXCHANGE_NEW_REQUEST';

UPDATE ember_schema.user_notification_settings
   SET notification_type = 'MOVEMENT_ADVANCED'
 WHERE notification_type = 'EXCHANGE_STATUS_CHANGE';

UPDATE ember_schema.notification
   SET type = 'MOVEMENT_RAISED'
 WHERE type = 'EXCHANGE_NEW_REQUEST';

UPDATE ember_schema.notification
   SET type = 'MOVEMENT_ADVANCED'
 WHERE type = 'EXCHANGE_STATUS_CHANGE';

-- The link a notification carries names a page rather than a path, and that page has moved.
UPDATE ember_schema.notification
   SET data = jsonb_set(data, '{route}', '"inventory-movements"')
 WHERE data ->> 'route' = 'inventory-exchanges';

-- 3. When a movement last moved, beside when it started.
--
-- A queue is read by what has gone quiet: a swap raised this morning and one raised in March both say
-- only their starting date, and the one that has been waiting three weeks for somebody to press a step
-- looks exactly like the one that moved an hour ago. The starting date cannot say that.
--
-- Existing rows take the time of the last thing that happened to them, which the log already records:
-- a step walked, forced or corrected, a refusal, a call-off. A movement nothing has ever happened to
-- takes the time it was started.

ALTER TABLE ember_schema.item_movement
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

COMMENT ON COLUMN ember_schema.item_movement.updated_at IS
    'When this movement last moved: a step acknowledged, forced or corrected, a refusal, a call-off or a '
    'move onto another chain. Set to the starting time for one nothing has happened to yet.';

UPDATE ember_schema.item_movement m
   SET updated_at = COALESCE(
           (SELECT MAX(l.changed_at) FROM ember_schema.item_movement_log l WHERE l.movement_id = m.id),
           m.created_at);
