-- An inventory and a kind inside one can carry a picture and a colour.
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
